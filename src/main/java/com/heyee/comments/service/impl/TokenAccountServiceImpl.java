package com.heyee.comments.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.heyee.comments.dto.Result;
import com.heyee.comments.dto.TokenAccountDTO;
import com.heyee.comments.dto.TokenRedeemDTO;
import com.heyee.comments.entity.TokenAccount;
import com.heyee.comments.entity.TokenTransaction;
import com.heyee.comments.entity.Voucher;
import com.heyee.comments.entity.VoucherOrder;
import com.heyee.comments.mapper.TokenAccountMapper;
import com.heyee.comments.mapper.TokenTransactionMapper;
import com.heyee.comments.mapper.VoucherOrderMapper;
import com.heyee.comments.mapper.VoucherMapper;
import com.heyee.comments.service.ITokenAccountService;
import com.heyee.comments.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Service
public class TokenAccountServiceImpl implements ITokenAccountService {

    private static final String REDEEM_TYPE = "TOKEN_PACK_REDEEM";

    @Resource private TokenAccountMapper tokenAccountMapper;
    @Resource private TokenTransactionMapper tokenTransactionMapper;
    @Resource private VoucherOrderMapper voucherOrderMapper;
    @Resource private VoucherMapper voucherMapper;
    @Resource private RedissonClient redissonClient;

    @Override
    public Result queryMyAccount() {
        return Result.ok(toAccountDTO(findAccount(UserHolder.getUser().getId())));
    }

    @Override
    public Result queryMyTokenOrders() {
        return Result.ok(voucherOrderMapper.queryTokenOrders(UserHolder.getUser().getId()));
    }

    @Override
    @Transactional
    public Result redeemOrder(Long orderId) {
        if (orderId == null) return Result.fail("订单号不能为空");
        Long userId = UserHolder.getUser().getId();
        RLock lock = redissonClient.getLock("lock:token:redeem:" + userId);
        if (!lock.tryLock()) return Result.fail("兑换请求处理中，请稍后重试");
        try {
            TokenTransaction existing = tokenTransactionMapper.selectOne(new QueryWrapper<TokenTransaction>()
                    .eq("source_order_id", orderId).eq("type", REDEEM_TYPE));
            if (existing != null) return Result.ok(toRedeemDTO(findAccount(userId), 0L, true));

            VoucherOrder order = voucherOrderMapper.selectById(orderId);
            if (order == null || !userId.equals(order.getUserId())) return Result.fail("Token 包订单不存在");
            // 当前秒杀订单没有独立支付确认；取消、退款中的订单不能兑换。
            if (order.getStatus() != null && order.getStatus() >= 4) return Result.fail("当前订单不能兑换 Token");
            Voucher voucher = voucherMapper.selectById(order.getVoucherId());
            if (voucher == null || voucher.getTokenAmount() == null || voucher.getTokenAmount() <= 0) {
                return Result.fail("该订单不是 Token 包订单");
            }

            TokenAccount account = findOrCreateAccount(userId);
            TokenTransaction transaction = new TokenTransaction();
            transaction.setUserId(userId);
            transaction.setAmount(voucher.getTokenAmount());
            transaction.setType(REDEEM_TYPE);
            transaction.setSourceOrderId(orderId);
            tokenTransactionMapper.insert(transaction);
            if (tokenAccountMapper.incrementBalance(userId, voucher.getTokenAmount()) != 1) {
                throw new IllegalStateException("Token 余额更新失败");
            }
            account.setBalance(account.getBalance() + voucher.getTokenAmount());
            return Result.ok(toRedeemDTO(account, voucher.getTokenAmount(), false));
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private TokenAccount findAccount(Long userId) {
        TokenAccount account = tokenAccountMapper.selectById(userId);
        return account == null ? new TokenAccount().setUserId(userId).setBalance(0L) : account;
    }

    private TokenAccount findOrCreateAccount(Long userId) {
        TokenAccount account = tokenAccountMapper.selectById(userId);
        if (account != null) return account;
        account = new TokenAccount();
        account.setUserId(userId);
        account.setBalance(0L);
        tokenAccountMapper.insert(account);
        return account;
    }

    private TokenAccountDTO toAccountDTO(TokenAccount account) {
        TokenAccountDTO dto = new TokenAccountDTO();
        dto.setBalance(account.getBalance());
        return dto;
    }

    private TokenRedeemDTO toRedeemDTO(TokenAccount account, Long amount, Boolean alreadyRedeemed) {
        TokenRedeemDTO dto = new TokenRedeemDTO();
        dto.setBalance(account.getBalance());
        dto.setRedeemedAmount(amount);
        dto.setAlreadyRedeemed(alreadyRedeemed);
        return dto;
    }
}
