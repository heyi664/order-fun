package com.heyee.comments.dto;

import lombok.Data;

@Data
public class AdminRegisterDTO {
    private String username;
    private String password;
    private String verificationCode;
}
