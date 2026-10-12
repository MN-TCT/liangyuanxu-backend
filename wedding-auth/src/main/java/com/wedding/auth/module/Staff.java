package com.wedding.auth.module;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("staff")
public class Staff {
    @TableId(type = IdType.AUTO)
    private Integer id;
    /** 归属商家(用户 id) */
    private Integer companyId;
    /** 员工用户 id(关联 sys_user) */
    private Integer userId;
    /** 职位：photographer/makeup_artist/host/cameraman */
    private String role;
    /** 1 在职 0 停用 */
    private Integer status;
    private LocalDateTime createdAt;
}
