package com.heyee.comments.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TokenPackageOrderDTO {
    private Long id;
    private Long voucherId;
    private String title;
    private Long tokenAmount;
    private Integer status;
    private Boolean redeemed;
    private LocalDateTime createTime;
}
