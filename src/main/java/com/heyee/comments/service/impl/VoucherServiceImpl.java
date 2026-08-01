package com.heyee.comments.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heyee.comments.dto.Result;
import com.heyee.comments.dto.TokenPackPublishDTO;
import com.heyee.comments.entity.SeckillVoucher;
import com.heyee.comments.entity.Voucher;
import com.heyee.comments.mapper.VoucherMapper;
import com.heyee.comments.service.ISeckillVoucherService;
import com.heyee.comments.service.IVoucherService;
import com.heyee.comments.service.cache.SeckillVoucherCacheService;
import com.heyee.comments.service.cache.VoucherListCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import static com.heyee.comments.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private VoucherListCacheService voucherListCacheService;
    @Resource
    private SeckillVoucherCacheService seckillVoucherCacheService;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = voucherListCacheService.getVoucherByShopId(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    public Result queryTokenPacks() {
        List<Voucher> packs = baseMapper.queryTokenPacks();
        for (Voucher pack : packs) {
            String stock = stringRedisTemplate.opsForValue().get(SECKILL_STOCK_KEY + pack.getId());
            if (stock != null) {
                try {
                    pack.setStock(Integer.valueOf(stock));
                } catch (NumberFormatException ignored) {
                    // Keep the database value when the cache value is malformed.
                }
            }
        }
        return Result.ok(packs);
    }

    @Override
    @Transactional
    public Result publishTokenPack(TokenPackPublishDTO request) {
        if (request == null || request.getTitle() == null || request.getTitle().trim().isEmpty()
                || request.getPrice() == null || request.getPrice().signum() <= 0
                || request.getTokenAmount() == null || request.getTokenAmount() <= 0
                || request.getStock() == null || request.getStock() <= 0
                || request.getPerOrderLimit() == null || request.getPerOrderLimit() <= 0
                || request.getPerUserLimit() == null || request.getPerUserLimit() < request.getPerOrderLimit()) {
            return Result.fail("Token 包参数不合法");
        }
        LocalDateTime beginTime = request.getBeginTime() == null ? LocalDateTime.now() : request.getBeginTime();
        LocalDateTime endTime = request.getEndTime();
        if (endTime == null || !endTime.isAfter(beginTime)) {
            return Result.fail("结束时间必须晚于开始时间");
        }
        long payValue;
        try {
            payValue = request.getPrice().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException ex) {
            return Result.fail("价格格式不合法");
        }
        Voucher voucher = new Voucher();
        voucher.setTitle(request.getTitle().trim());
        voucher.setSubTitle(request.getDescription());
        voucher.setRules("Token 包兑换后将增加账户 Token 余额");
        voucher.setPayValue(payValue);
        voucher.setActualValue(0L);
        voucher.setTokenAmount(request.getTokenAmount());
        voucher.setPerOrderLimit(request.getPerOrderLimit());
        voucher.setPerUserLimit(request.getPerUserLimit());
        voucher.setType(1);
        voucher.setStatus(1);
        voucher.setStock(request.getStock());
        voucher.setBeginTime(beginTime);
        voucher.setEndTime(endTime);
        addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        // 保存秒杀库存到Redis中
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                try {
                    seckillVoucherCacheService.initializePublishedVoucher(voucher);
                } catch (Exception ex) {
                    log.error("Failed to initialize Redis cache for published token package, voucherId={}",
                            voucher.getId(), ex);
                }
            }
        });
        voucherListCacheService.evictVoucherList(voucher.getShopId());
    }
}
