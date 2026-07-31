package com.heyee.comments.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heyee.comments.dto.Result;
import com.heyee.comments.entity.VoucherOrder;
import com.heyee.comments.mapper.VoucherOrderMapper;
import com.heyee.comments.service.ISeckillVoucherService;
import com.heyee.comments.service.IVoucherOrderService;
import com.heyee.comments.utils.RedisIdWorker;
import com.heyee.comments.utils.RocketMqConstants;
import com.heyee.comments.utils.UserHolder;
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

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        if (result == null) {
            return Result.fail("秒杀服务暂时不可用");
        }
        if (result == 1) {
            return Result.fail("库存不足");
        }
        if (result == 2) {
            return Result.fail("不能重复下单");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        try {
            rocketMQTemplate.syncSend(RocketMqConstants.SECKILL_VOUCHER_ORDER_TOPIC, voucherOrder);
        } catch (Exception e) {
            log.error("秒杀订单消息投递失败，orderId={}", orderId, e);
            throw new IllegalStateException("秒杀订单提交失败，请稍后重试", e);
        }
        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        if (!redisLock.tryLock()) {
            throw new IllegalStateException("获取秒杀订单锁失败");
        }
        try {
            boolean exists = query()
                    .eq("user_id", userId)
                    .eq("voucher_id", voucherId)
                    .count() > 0;
            if (exists) {
                log.info("忽略重复秒杀消息：userId={}, voucherId={}", userId, voucherId);
                return;
            }
            boolean stockUpdated = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            if (!stockUpdated) {
                throw new IllegalStateException("数据库库存扣减失败");
            }
            if (!save(voucherOrder)) {
                throw new IllegalStateException("秒杀订单保存失败");
            }
        } finally {
            redisLock.unlock();
        }
    }
}
