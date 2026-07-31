package com.heyee.comments.service;

import com.heyee.comments.dto.Result;

public interface ITokenAccountService {
    Result queryMyAccount();
    Result redeemOrder(Long orderId);
    Result queryMyTokenOrders();
}
