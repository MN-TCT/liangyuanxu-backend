package com.wedding.message.module;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("conversation")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Long userId;
    private Long companyId;
    private Long packageId;
    private String userName;
    private String userPhone;
    /** 0进行中 1已关闭 */
    private Integer status;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Long unreadCustomer;

    /** 商家名称（婚庆公司 shop_name，非表字段） */
    @TableField(exist = false)
    private String companyName;

    /** 商家头像（非表字段） */
    @TableField(exist = false)
    private String companyAvatar;

    /** 套餐名称（非表字段） */
    @TableField(exist = false)
    private String packageName;
}
