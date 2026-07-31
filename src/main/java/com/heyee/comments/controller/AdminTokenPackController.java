package com.heyee.comments.controller;

import com.heyee.comments.dto.Result;
import com.heyee.comments.dto.TokenPackPublishDTO;
import com.heyee.comments.service.IAdminAuthService;
import com.heyee.comments.service.IVoucherService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/admin/token-packs")
public class AdminTokenPackController {
    @Resource private IVoucherService voucherService;
    @Resource private IAdminAuthService adminAuthService;

    @PostMapping
    public Result publish(@RequestHeader(value = "authorization", required = false) String token,
                          @RequestBody TokenPackPublishDTO request) {
        if (!adminAuthService.isAdminToken(token)) return Result.fail("请使用管理员账号登录后发布");
        return voucherService.publishTokenPack(request);
    }
}
