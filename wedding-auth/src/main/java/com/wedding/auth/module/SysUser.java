package com.wedding.auth.module;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String shopName;
    private String contactName;
    private String phone;
    private String address;
    private String businessType;
    private String licenseNo;
    private String licenseImg;
    private String avatar;
    private String introduction;
    private String role;
    private String openid;
    private Integer status;
    /** 首页商家展示开关：1=展示，0=不展示 */
    private Integer homeShow;
    /** 首页商家排序（越小越靠前） */
    private Integer homeSort;
    private LocalDateTime createdAt;
}
