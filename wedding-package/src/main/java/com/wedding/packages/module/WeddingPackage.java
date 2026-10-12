package com.wedding.packages.module;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("wedding_package")
public class WeddingPackage {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer categoryId;
    private String name;
    private String coverUrl;
    /** 多张封面图，JSON 数组字符串（第一张与 coverUrl 保持一致，用于列表缩略图） */
    private String coverUrls;
    /** 关联婚庆视频地址（用户端播放） */
    private String videoUrl;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer durationDays;
    private String location;
    /** 包含内容，JSON 字符串 */
    private String includesJson;
    private Integer stock;
    /** 1上架 0下架 */
    private Integer status;
    /** 是否置顶到首页 1是 0否 */
    private Integer homePinned;
    /** 置顶排序，越小越前（仅 homePinned=1 时有效） */
    private Integer homeSort;
    /** 归属商家(用户 id) */
    private Integer companyId;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String categoryName;
    /** 商家店铺名称（冗余查询，非表字段） */
    @TableField(exist = false)
    private String companyName;
    /** 商家头像（冗余查询，非表字段） */
    @TableField(exist = false)
    private String companyAvatar;
    /** 商家地址（冗余查询，非表字段） */
    @TableField(exist = false)
    private String companyAddress;
}
