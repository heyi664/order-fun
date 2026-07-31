package com.heyee.comments.service;

import com.heyee.comments.dto.AdminLoginDTO;
import com.heyee.comments.dto.AdminRegisterDTO;
import com.heyee.comments.dto.Result;

public interface IAdminAuthService {
    Result login(AdminLoginDTO request);
    Result register(AdminRegisterDTO request);
    Result updateRegistrationCode(String token, String verificationCode);
    Result logout(String token);
    boolean isAdminToken(String token);
}
