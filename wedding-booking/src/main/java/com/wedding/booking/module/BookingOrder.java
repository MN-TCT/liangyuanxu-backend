package com.wedding.booking.module;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("booking_order")
public class BookingOrder {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String customerName;
    private String phone;
    private Integer packageId;
    private String packageName;
    /** 套餐内容（JSON 或文本） */
    private String packageContent;
    /** 婚纱套数 */
    private Integer dressCount;
    /** 拍摄位置 */
    private String shootLocation;
    /** 场景类型：indoor 内景 / outdoor 外景 / both 内外景 */
    private String sceneType;
    /** 订单价格 */
    private java.math.BigDecimal price;
    /** 所选婚纱（JSON 数组字符串，如 [{"name":"主纱","count":1}]） */
    private String dresses;
    /** 来源：0=客户预约 1=商家创建 */
    private Integer source;
    private LocalDate planDate;
    /** 0待处理 1已联系 2已成交 3已取消 */
    private Integer status;
    private String remark;
    /** 归属商家(用户 id) */
    private Integer companyId;
    /** 执行员工用户 id */
    private Integer staffId;
    private String staffName;
    private String staffRole;
    private LocalDateTime createdAt;
}
