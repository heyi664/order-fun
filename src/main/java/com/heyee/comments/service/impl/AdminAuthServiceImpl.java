package com.heyee.comments.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.heyee.comments.dto.AdminLoginDTO;
import com.heyee.comments.dto.AdminRegisterDTO;
import com.heyee.comments.dto.Result;
import com.heyee.comments.entity.AdminSetting;
import com.heyee.comments.entity.AdminUser;
import com.heyee.comments.mapper.AdminSettingMapper;
import com.heyee.comments.mapper.AdminUserMapper;
import com.heyee.comments.service.IAdminAuthService;
import com.heyee.comments.utils.PasswordHashUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.heyee.comments.utils.RedisConstants.ADMIN_LOGIN_TOKEN_KEY;
import static com.heyee.comments.utils.RedisConstants.LOGIN_USER_TTL;

@Service
public class AdminAuthServiceImpl implements IAdminAuthService {
    private static final String REGISTRATION_CODE_KEY = "admin_registration_code";

    @Resource private AdminUserMapper adminUserMapper;
    @Resource private AdminSettingMapper adminSettingMapper;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Value("${heyee.admin.bootstrap-registration-code:}") private String bootstrapRegistrationCode;

    @Override
    public Result login(AdminLoginDTO request) {
        String username = normalizeUsername(request == null ? null : request.getUsername());
        String password = request == null ? null : request.getPassword();
        if (username == null || StrUtil.isBlank(password)) return Result.fail("请输入管理员账号和密码");
        AdminUser user = adminUserMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AdminUser>().eq("username", username));
        if (user == null || !PasswordHashUtils.matches(password, user.getPasswordHash())) return Result.fail("管理员账号或密码错误");
        return Result.ok(createSession(user));
    }

    @Override
    public Result register(AdminRegisterDTO request) {
        String username = normalizeUsername(request == null ? null : request.getUsername());
        String password = request == null ? null : request.getPassword();
        String verificationCode = request == null ? null : request.getVerificationCode();
        if (username == null || StrUtil.isBlank(password) || StrUtil.isBlank(verificationCode)) return Result.fail("账号、密码和注册校验码均为必填项");
        if (password.length() < 8) return Result.fail("管理员密码至少需要 8 位");
        if (adminUserMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AdminUser>().eq("username", username)) > 0) return Result.fail("该管理员账号已存在");

        long adminCount = adminUserMapper.selectCount(null);
        String storedCodeHash = adminCount == 0 ? null : getRegistrationCodeHash();
        boolean codeValid = adminCount == 0
                ? StrUtil.isNotBlank(bootstrapRegistrationCode) && bootstrapRegistrationCode.equals(verificationCode)
                : storedCodeHash != null && PasswordHashUtils.matches(verificationCode, storedCodeHash);
        if (!codeValid) return Result.fail(adminCount == 0 ? "首次注册校验码错误或未配置" : "管理员注册校验码错误");

        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setPasswordHash(PasswordHashUtils.hash(password));
        adminUserMapper.insert(user);
        if (adminCount == 0) saveRegistrationCodeHash(verificationCode, user.getId());
        return Result.ok(createSession(user));
    }

    @Override
    public Result updateRegistrationCode(String token, String verificationCode) {
        Long adminId = getAdminId(token);
        if (adminId == null) return Result.fail("管理员登录已失效");
        if (StrUtil.isBlank(verificationCode) || verificationCode.trim().length() < 6) return Result.fail("注册校验码至少需要 6 位");
        saveRegistrationCodeHash(verificationCode.trim(), adminId);
        return Result.ok();
    }

    @Override
    public Result logout(String token) {
        if (StrUtil.isBlank(token)) return Result.ok();
        stringRedisTemplate.delete(ADMIN_LOGIN_TOKEN_KEY + token);
        return Result.ok();
    }

    @Override
    public boolean isAdminToken(String token) {
        return getAdminId(token) != null;
    }

    private String createSession(AdminUser user) {
        String token = UUID.randomUUID().toString(true);
        Map<String, String> session = new HashMap<>();
        session.put("id", String.valueOf(user.getId()));
        session.put("nickName", user.getUsername());
        session.put("icon", "");
        stringRedisTemplate.opsForHash().putAll(ADMIN_LOGIN_TOKEN_KEY + token, session);
        stringRedisTemplate.expire(ADMIN_LOGIN_TOKEN_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return token;
    }

    private Long getAdminId(String token) {
        if (StrUtil.isBlank(token)) return null;
        Object value = stringRedisTemplate.opsForHash().get(ADMIN_LOGIN_TOKEN_KEY + token, "id");
        try { return value == null ? null : Long.valueOf(value.toString()); } catch (NumberFormatException e) { return null; }
    }

    private String getRegistrationCodeHash() {
        AdminSetting setting = adminSettingMapper.selectById(REGISTRATION_CODE_KEY);
        return setting == null ? null : setting.getSettingValue();
    }

    private void saveRegistrationCodeHash(String verificationCode, Long adminId) {
        AdminSetting setting = new AdminSetting();
        setting.setSettingKey(REGISTRATION_CODE_KEY);
        setting.setSettingValue(PasswordHashUtils.hash(verificationCode));
        setting.setUpdatedBy(adminId);
        if (adminSettingMapper.selectById(REGISTRATION_CODE_KEY) == null) adminSettingMapper.insert(setting);
        else adminSettingMapper.updateById(setting);
    }

    private String normalizeUsername(String username) {
        if (StrUtil.isBlank(username)) return null;
        String normalized = username.trim();
        return normalized.matches("[A-Za-z0-9_.-]{3,32}") ? normalized : null;
    }
}
