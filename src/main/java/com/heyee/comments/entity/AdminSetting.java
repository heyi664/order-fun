package com.heyee.comments.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_admin_setting")
public class AdminSetting {
    @TableId
    private String settingKey;
    private String settingValue;
    private Long updatedBy;
    private LocalDateTime updateTime;
}
