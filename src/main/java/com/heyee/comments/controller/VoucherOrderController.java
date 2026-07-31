package com.heyee.comments.controller;


import com.heyee.comments.dto.Result;
import com.heyee.comments.limiter.annotation.RateLimiter;
import com.heyee.comments.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    @RateLimiter(key = "rate_limit:seckill:", window = 10, limit = 5,
            type = RateLimiter.LimitType.USER, message = "秒杀请求过于频繁，请稍后再试")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }
}
