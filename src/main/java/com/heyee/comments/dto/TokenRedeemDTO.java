package com.heyee.comments.dto;

import lombok.Data;

@Data
public class TokenRedeemDTO {
    private Long balance;
    private Long redeemedAmount;
    private Boolean alreadyRedeemed;
}
