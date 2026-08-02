package com.heyee.comments.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Public data for a login image captcha. The expected answer remains in Redis. */
@Data
@AllArgsConstructor
public class ImageCaptchaDTO {
    private String captchaId;
    private String image;
}
