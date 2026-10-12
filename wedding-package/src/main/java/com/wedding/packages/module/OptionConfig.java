package com.wedding.packages.module;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 下拉选项配置（供套餐编辑下拉选择，商家可自定义维护）
 * category: location=拍摄地点 / days=拍摄天数 / service=包含服务
 */
@Data
@TableName("option_config")
public class OptionConfig {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String category;
    private String label;
    private String value;
    private Integer sort;
    private LocalDateTime createdAt;
}
