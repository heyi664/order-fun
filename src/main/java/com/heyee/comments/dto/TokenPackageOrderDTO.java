package com.heyee.comments.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TokenPackageOrderDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private Long voucherId;
    private String title;
    private Long tokenAmount;
    private Integer quantity;
    private Integer status;
    private Boolean redeemed;
    private LocalDateTime createTime;
}
