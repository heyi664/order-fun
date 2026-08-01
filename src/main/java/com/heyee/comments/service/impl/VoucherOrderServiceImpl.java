package com.heyee.comments.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heyee.comments.dto.Result;
import com.heyee.comments.entity.Voucher;
import com.heyee.comments.entity.VoucherOrder;
import com.heyee.comments.mapper.VoucherMapper;
import com.heyee.comments.mapper.VoucherOrderMapper;
import com.heyee.comments.service.ISeckillVoucherService;
import com.heyee.comments.service.IVoucherOrderService;
import com.heyee.comments.service.cache.SeckillVoucherCacheService;
import com.heyee.comments.service.cache.SeckillVoucherCacheService.Metadata;
import com.heyee.comments.utils.RedisIdWorker;
import com.heyee.comments.utils.RocketMqConstants;
import com.heyee.comments.utils.UserHolder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    private static final String METRIC_TOTAL = "seckill.order.request.duration";
    private static final String METRIC_VOUCHER_CACHE = "seckill.voucher.cache.duration";
    private static final String METRIC_VOUCHER_DB_FALLBACK = "seckill.voucher.db-fallback.duration";
    private static final String METRIC_REDIS_LUA = "seckill.redis.lua.duration";
    private static final String METRIC_ROCKETMQ_SEND = "seckill.rocketmq.sync-send.duration";

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Resource private ISeckillVoucherService seckillVoucherService;
    @Resource private RedisIdWorker redisIdWorker;
    @Resource private RedissonClient redissonClient;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private RocketMQTemplate rocketMQTemplate;
    @Resource private VoucherMapper voucherMapper;
    @Resource private MeterRegistry meterRegistry;
    @Resource private SeckillVoucherCacheService seckillVoucherCacheService;

    @Override
    public Result seckillVoucher(Long voucherId) {
        return seckillVoucher(voucherId, 1);
    }

    @Override
    public Result checkSeckillEligibility(Long voucherId, Integer quantity) {
        if (quantity == null || quantity <= 0) return Result.fail("Purchase quantity must be greater than zero");
        Metadata voucher = getVoucherMetadata(voucherId);
        if (voucher == null || voucher.getType() != 1 || voucher.getStatus() != 1) {
            return Result.fail("Token package is unavailable");
        }
        long now = System.currentTimeMillis();
        if (now < voucher.getBeginAt()) {
            return Result.fail("Token package sale has not started");
        }
        if (now >= voucher.getEndAt()) {
            return Result.fail("Token package sale has ended");
        }
        int perOrderLimit = voucher.getPerOrderLimit();
        int perUserLimit = voucher.getPerUserLimit();
        if (quantity > perOrderLimit) return Result.fail("Purchase quantity exceeds the per-order limit");
        String stock = stringRedisTemplate.opsForValue().get(com.heyee.comments.utils.RedisConstants.SECKILL_STOCK_KEY + voucherId);
        if (stock == null || Long.parseLong(stock) < quantity) return Result.fail("Token package is sold out");
        Long userId = UserHolder.getUser().getId();
        Object boughtValue = stringRedisTemplate.opsForHash().get(
                com.heyee.comments.utils.RedisConstants.SECKILL_USER_COUNT_KEY + voucherId, userId.toString());
        long bought = boughtValue == null ? 0L : Long.parseLong(boughtValue.toString());
        if (bought + quantity > perUserLimit) return Result.fail("Purchase quantity exceeds the personal limit");
        return Result.ok();
    }

    @Override
    public Result seckillVoucher(Long voucherId, Integer quantity) {
        return seckillVoucher(voucherId, quantity, UUID.randomUUID().toString());
    }

    @Override
    public Result seckillVoucher(Long voucherId, Integer quantity, String paymentRequestId) {
        Timer.Sample totalSample = Timer.start(meterRegistry);
        try {
            if (paymentRequestId == null || !paymentRequestId.matches("[A-Za-z0-9-]{16,64}")) {
                return Result.fail("Invalid payment request");
            }
            if (quantity == null || quantity <= 0) return Result.fail("购买数量必须大于 0");

            Metadata voucher = getVoucherMetadata(voucherId);
            if (voucher == null || voucher.getType() != 1 || voucher.getStatus() != 1) {
                return Result.fail("Token 包不存在或已下架");
            }
            long now = System.currentTimeMillis();
            if (now < voucher.getBeginAt()) {
                return Result.fail("Token package sale has not started");
            }
            if (now >= voucher.getEndAt()) {
                return Result.fail("Token package sale has ended");
            }
            int perOrderLimit = voucher.getPerOrderLimit();
            int perUserLimit = voucher.getPerUserLimit();
            if (quantity > perOrderLimit) return Result.fail("超过单次限购数量");

            Long userId = UserHolder.getUser().getId();
            String paymentKey = com.heyee.comments.utils.RedisConstants.SECKILL_PAYMENT_REQUEST_KEY
                    + userId + ":" + paymentRequestId;
            Boolean firstConfirmation = stringRedisTemplate.opsForValue().setIfAbsent(
                    paymentKey, "PROCESSING", 30, TimeUnit.MINUTES);
            if (!Boolean.TRUE.equals(firstConfirmation)) {
                String previousResult = stringRedisTemplate.opsForValue().get(paymentKey);
                if (previousResult != null && previousResult.startsWith("ORDER:")) {
                    return Result.ok(Long.valueOf(previousResult.substring("ORDER:".length())));
                }
                return Result.fail("Payment confirmation is already being processed");
            }
            long orderId = redisIdWorker.nextId("order");
            Timer.Sample redisLuaSample = Timer.start(meterRegistry);
            Long result;
            try {
                result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(),
                        voucherId.toString(), userId.toString(), String.valueOf(orderId), String.valueOf(quantity),
                        String.valueOf(perOrderLimit), String.valueOf(perUserLimit),
                        String.valueOf(voucher.getEndAt() / 1000));
            } finally {
                redisLuaSample.stop(timer(METRIC_REDIS_LUA));
            }
            if (result == null || result != 0) stringRedisTemplate.delete(paymentKey);
            if (result == null) return Result.fail("秒杀服务暂时不可用");
            if (result == 1) return Result.fail("库存不足");
            if (result == 2) return Result.fail("超过每人累计限购数量");
            if (result == 3) return Result.fail("超过单次限购数量");

            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setId(orderId);
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            voucherOrder.setQuantity(quantity);
            Timer.Sample rocketMqSample = Timer.start(meterRegistry);
            try {
                rocketMQTemplate.syncSend(RocketMqConstants.SECKILL_VOUCHER_ORDER_TOPIC, voucherOrder);
            } catch (Exception e) {
                log.error("秒杀订单消息投递失败，orderId={}", orderId, e);
                throw new IllegalStateException("秒杀订单提交失败，请稍后重试", e);
            } finally {
                rocketMqSample.stop(timer(METRIC_ROCKETMQ_SEND));
            }
            stringRedisTemplate.opsForValue().set(paymentKey, "ORDER:" + orderId, 24, TimeUnit.HOURS);
            return Result.ok(orderId);
        } finally {
            totalSample.stop(timer(METRIC_TOTAL));
        }
    }

    /**
     * Reads sale metadata from Redis on the normal path. For packages published
     * before this cache was introduced, a one-time database fallback backfills
     * metadata only; it never recreates the Redis stock key.
     */
    private Metadata getVoucherMetadata(Long voucherId) {
        Timer.Sample cacheSample = Timer.start(meterRegistry);
        Metadata metadata;
        try {
            metadata = seckillVoucherCacheService.get(voucherId);
        } finally {
            cacheSample.stop(timer(METRIC_VOUCHER_CACHE));
        }
        if (metadata != null) {
            meterRegistry.counter("seckill.voucher.cache.hit").increment();
            return metadata;
        }

        meterRegistry.counter("seckill.voucher.cache.miss").increment();
        Timer.Sample fallbackSample = Timer.start(meterRegistry);
        try {
            metadata = seckillVoucherCacheService.loadMetadataFromDatabase(voucherId);
        } finally {
            fallbackSample.stop(timer(METRIC_VOUCHER_DB_FALLBACK));
        }
        if (metadata != null && metadata.getEndAt() > System.currentTimeMillis()) {
            seckillVoucherCacheService.cacheMetadata(metadata);
        }
        return metadata;
    }

    private Timer timer(String name) {
        return Timer.builder(name)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        int quantity = voucherOrder.getQuantity() == null ? 1 : voucherOrder.getQuantity();
        RLock redisLock = redissonClient.getLock("lock:order:" + userId + ":" + voucherId);
        if (!redisLock.tryLock()) throw new IllegalStateException("获取秒杀订单锁失败");
        try {
            if (getById(voucherOrder.getId()) != null) {
                log.info("忽略重复秒杀消息：orderId={}", voucherOrder.getId());
                return;
            }
            Voucher voucher = voucherMapper.selectById(voucherId);
            if (voucher == null) throw new IllegalStateException("Token 包不存在");
            int perUserLimit = voucher.getPerUserLimit() == null ? 1 : voucher.getPerUserLimit();
            Long purchased = baseMapper.sumQuantityByUserVoucher(userId, voucherId);
            if ((purchased == null ? 0L : purchased) + quantity > perUserLimit) {
                throw new IllegalStateException("超过每人累计限购数量");
            }
            boolean stockUpdated = seckillVoucherService.update()
                    .setSql("stock = stock - " + quantity)
                    .eq("voucher_id", voucherId).ge("stock", quantity).update();
            if (!stockUpdated) throw new IllegalStateException("数据库库存扣减失败");
            if (!save(voucherOrder)) throw new IllegalStateException("秒杀订单保存失败");
        } finally {
            redisLock.unlock();
        }
    }
}
