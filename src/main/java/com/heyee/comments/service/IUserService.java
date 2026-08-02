package com.heyee.comments.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heyee.comments.dto.ImageCaptchaDTO;
import com.heyee.comments.dto.LoginFormDTO;
import com.heyee.comments.dto.Result;
import com.heyee.comments.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    ImageCaptchaDTO createImageCaptcha();

    Result sendCode(String email, String captchaId, String captchaCode);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result logout(String token);

    Result sign();

    Result signCount();

}
