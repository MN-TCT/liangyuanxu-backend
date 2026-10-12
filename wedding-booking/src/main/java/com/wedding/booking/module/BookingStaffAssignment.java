package com.wedding.booking.module;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("booking_staff_assignment")
public class BookingStaffAssignment {
    @TableId(type = IdType.AUTO)
    private Integer id;
    /** 订单 id */
    private Integer bookingId;
    /** 归属商家(用户 id) */
    private Integer companyId;
    /** 职位：photographer/makeup_artist/host/... */
    private String role;
    /** 员工用户 id */
    private Integer staffId;
    private String staffName;
    private LocalDateTime createdAt;
}
