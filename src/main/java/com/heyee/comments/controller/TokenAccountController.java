package com.heyee.comments.controller;

import com.heyee.comments.dto.Result;
import com.heyee.comments.service.ITokenAccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/token-account")
public class TokenAccountController {
    @Resource private ITokenAccountService tokenAccountService;
    @GetMapping public Result queryMyAccount() { return tokenAccountService.queryMyAccount(); }
    @GetMapping("/orders") public Result queryMyTokenOrders() { return tokenAccountService.queryMyTokenOrders(); }
    @PostMapping("/redeem/{orderId}") public Result redeem(@PathVariable Long orderId) { return tokenAccountService.redeemOrder(orderId); }
}
