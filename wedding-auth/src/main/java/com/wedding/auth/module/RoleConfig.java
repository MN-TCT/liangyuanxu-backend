package com.wedding.auth.module;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 技术职位配置（按商家独立维护，基础配置可自定义职位） */
@Data
@TableName("staff_role_config")
public class RoleConfig {
    @TableId(type = IdType.AUTO)
    private Integer id;
    /** 归属商家(用户 id)；0 = 平台默认模板 */
    private Integer companyId;
    /** 职位唯一标识（code，存储于 staff/申请 的 role 字段，同一商家内唯一） */
    private String roleCode;
    /** 职位名称 */
    private String roleName;
    /** 服务费/酬劳（该职位的费用） */
    private BigDecimal fee;
    /** 费用是否对技术人员可见：1 可见 0 隐藏 */
    private Integer feeVisible;
    private Integer sort;
    private LocalDateTime createdAt;
}
