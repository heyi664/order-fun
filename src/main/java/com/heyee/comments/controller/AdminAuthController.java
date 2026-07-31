package com.heyee.comments.controller;

import com.heyee.comments.dto.AdminLoginDTO;
import com.heyee.comments.dto.AdminRegisterDTO;
import com.heyee.comments.dto.AdminRegistrationCodeDTO;
import com.heyee.comments.dto.Result;
import com.heyee.comments.service.IAdminAuthService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {
    @Resource private IAdminAuthService adminAuthService;

    @PostMapping("/login")
    public Result login(@RequestBody AdminLoginDTO request) {
        return adminAuthService.login(request);
    }

    @PostMapping("/register")
    public Result register(@RequestBody AdminRegisterDTO request) {
        return adminAuthService.register(request);
    }

    @PostMapping("/registration-code")
    public Result updateRegistrationCode(@RequestHeader(value = "authorization", required = false) String token,
                                         @RequestBody AdminRegistrationCodeDTO request) {
        return adminAuthService.updateRegistrationCode(token, request == null ? null : request.getVerificationCode());
    }

    @PostMapping("/logout")
    public Result logout(@RequestHeader(value = "authorization", required = false) String token) {
        return adminAuthService.logout(token);
    }
}
