package com.wedding.auth.module;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单配置：管理员可控制用户端(小程序)与商家端(后台)的菜单显示。
 * 支持树形结构（parentId 自关联）、图标、排序、可见性，以及按角色控制。
 */
@Data
@TableName("menu_config")
public class MenuConfig {
    @TableId(type = IdType.AUTO)
    private Integer id;
    /** 所属端：user=用户端(小程序) / merchant=商家端(后台) */
    private String side;
    /** 父菜单 id；0 = 顶级 */
    private Integer parentId;
    /** 菜单标识（唯一，前端据此映射） */
    private String code;
    /** 菜单名称 */
    private String name;
    /** 图标（emoji 或 lucide 图标名） */
    private String icon;
    /** 跳转路径 / 页面路径 */
    private String path;
    /** 页面组件路径（商家端后台动态路由用），如 wedding/stats/index */
    private String component;
    /** 路由 name（与前端静态路由 name 一致，mixed 模式下用于合并去重），如 WeddingPackage */
    private String routeName;
    /** 可见角色，逗号分隔；空 = 全部角色可见。如 customer,wedding_company */
    private String roles;
    /** 是否可见 1是 0否 */
    private Integer visible;
    /** 是否启用（停用后前端忽略） 1是 0否 */
    private Integer enabled;
    /** 排序，越小越前 */
    private Integer sort;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 子菜单（非表字段，组装树用） */
    @TableField(exist = false)
    private List<MenuConfig> children;
}
