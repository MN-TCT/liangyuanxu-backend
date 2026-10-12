package com.wedding.auth.module;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("staff_request")
public class StaffRequest {
    @TableId(type = IdType.AUTO)
    private Integer id;
    /** 目标婚庆公司(用户 id) */
    private Integer companyId;
    /** 申请人(技术用户 id) */
    private Integer userId;
    /** 申请的职位：photographer/makeup_artist/host/... */
    private String role;
    /** 0 待审核 1 通过 2 拒绝 */
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
