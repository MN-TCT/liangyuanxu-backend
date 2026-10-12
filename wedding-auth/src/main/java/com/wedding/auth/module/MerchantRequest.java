package com.wedding.auth.module;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家入驻申请：普通用户申请成为婚庆公司(商家)，管理员审核通过后账号角色转为 wedding_company。
 */
@Data
@TableName("merchant_request")
public class MerchantRequest {
    @TableId(type = IdType.AUTO)
    private Integer id;
    /** 申请人(用户 id) */
    private Integer userId;
    /** 申请的店铺名称 */
    private String shopName;
    /** 联系人姓名 */
    private String contactName;
    /** 申请手机号 */
    private String phone;
    /** 店铺地址 */
    private String address;
    /** 主营服务类型 */
    private String businessType;
    /** 营业执照号 */
    private String licenseNo;
    /** 营业执照图片(相对路径) */
    private String licenseImg;
    /** 店铺简介 */
    private String introduction;
    /** 0 待审核 1 通过 2 拒绝 */
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
