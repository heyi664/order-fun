package com.heyee.comments.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heyee.comments.dto.LoginFormDTO;
import com.heyee.comments.dto.ImageCaptchaDTO;
import com.heyee.comments.dto.Result;
import com.heyee.comments.dto.UserDTO;
import com.heyee.comments.entity.User;
import com.heyee.comments.mapper.UserMapper;
import com.heyee.comments.service.IUserService;
import com.heyee.comments.service.captcha.LoginCaptchaService;
import com.heyee.comments.utils.RegexUtils;
import com.heyee.comments.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.heyee.comments.utils.RedisConstants.*;
import static com.heyee.comments.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JavaMailSender mailSender;

    @Resource
    private LoginCaptchaService loginCaptchaService;

    @Value("${heyee.mail.from:}")
    private String mailFrom;

    @Override
    public ImageCaptchaDTO createImageCaptcha() {
        return loginCaptchaService.create();
    }

    @Override
    public Result sendCode(String email, String captchaId, String captchaCode) {
        if (!loginCaptchaService.verifyAndConsume(captchaId, captchaCode)) {
            return Result.fail("图形验证码错误或已过期，请刷新后重试");
        }
        email = email == null ? null : email.trim().toLowerCase();
        // 1.校验邮箱
        if (RegexUtils.isEmailInvalid(email)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("邮箱格式错误！");
        }
        Boolean sendLockAcquired = stringRedisTemplate.opsForValue().setIfAbsent(
                LOGIN_CODE_SEND_LOCK_KEY + email, "1", 60, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(sendLockAcquired)) {
            return Result.fail("验证码已发送，请稍后再试");
        }
        // 3.生成验证码并发送邮件
        String code = RandomUtil.randomNumbers(6);
        try {
            sendLoginCodeEmail(email, code);
            stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + email, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        } catch (RuntimeException e) {
            stringRedisTemplate.delete(LOGIN_CODE_SEND_LOCK_KEY + email);
            log.error("failed to send login verification email to {}", email, e);
            return Result.fail("验证码邮件发送失败，请稍后重试");
        }

        // 验证码已保存到 Redis
        // 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验邮箱
        String email = loginForm.getEmail() == null ? null : loginForm.getEmail().trim().toLowerCase();
        if (RegexUtils.isEmailInvalid(email)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("邮箱格式错误！");
        }
        // 3.从redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + email);
        String code = loginForm.getCode() == null ? null : loginForm.getCode().trim();
        if (RegexUtils.isCodeInvalid(code) || cacheCode == null || !cacheCode.equals(code)) {
            log.debug("login code mismatch, email={}", email);
            // 不一致，报错
            return Result.fail("验证码错误");
        }
        stringRedisTemplate.delete(LOGIN_CODE_KEY + email);

        // 4.一致，根据邮箱查询用户
        User user = query().eq("email", email).one();

        // 5.判断用户是否存在
        if (user == null) {
            // 6.不存在，创建新用户并保存
            user = createUserWithEmail(email);
        }

        // 7.保存用户信息到 redis中
        // 7.1.随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2.将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 7.3.存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4.设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8.返回token
        return Result.ok(token);
    }

    @Override
    public Result logout(String token) {
        if (cn.hutool.core.util.StrUtil.isBlank(token)) {
            return Result.fail("登录令牌不能为空");
        }
        stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        return Result.ok();
    }

    @Override
    public Result sign() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6.循环遍历
        int count = 0;
        while (true) {
            // 6.1.让这个数字与1做与运算，得到数字的最后一个bit位  // 判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 如果为0，说明未签到，结束
                break;
            }else {
                // 如果不为0，说明已签到，计数器+1
                count++;
            }
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private void sendLoginCodeEmail(String email, String code) {
        if (cn.hutool.core.util.StrUtil.isBlank(mailFrom)) {
            throw new IllegalStateException("MAIL_FROM must be configured");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("HYEEE 登录验证码");
        message.setText("您的登录验证码是：" + code + "。验证码 " + LOGIN_CODE_TTL + " 分钟内有效，请勿向他人泄露。");
        mailSender.send(message);
    }

    private User createUserWithEmail(String email) {
        // 1.创建用户
        User user = new User();
        user.setEmail(email);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2.保存用户
        save(user);
        return user;
    }
}
