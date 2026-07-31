package com.heyee.comments.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heyee.comments.entity.TokenAccount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface TokenAccountMapper extends BaseMapper<TokenAccount> {
    @Update("UPDATE tb_user_token_account SET balance = balance + #{amount} WHERE user_id = #{userId}")
    int incrementBalance(@Param("userId") Long userId, @Param("amount") Long amount);
}
