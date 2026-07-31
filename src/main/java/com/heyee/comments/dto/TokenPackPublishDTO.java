package com.heyee.comments.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TokenPackPublishDTO {
    private String title;
    private String description;
    /** Price in yuan, e.g. 9.90. */
    private BigDecimal price;
    private Long tokenAmount;
    private Integer stock;
    private Integer perOrderLimit;
    private Integer perUserLimit;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
}
