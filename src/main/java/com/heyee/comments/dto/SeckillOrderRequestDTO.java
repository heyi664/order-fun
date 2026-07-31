package com.heyee.comments.dto;

import lombok.Data;

@Data
public class SeckillOrderRequestDTO {
    private Integer quantity;
    private String paymentRequestId;
}
