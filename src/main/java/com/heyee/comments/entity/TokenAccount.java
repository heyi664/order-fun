package com.heyee.comments.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("tb_user_token_account")
public class TokenAccount {
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;
    private Long balance;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
