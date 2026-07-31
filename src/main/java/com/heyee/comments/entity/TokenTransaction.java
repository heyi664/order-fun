package com.heyee.comments.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_token_transaction")
public class TokenTransaction {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long amount;
    private String type;
    private Long sourceOrderId;
    private LocalDateTime createTime;
}
