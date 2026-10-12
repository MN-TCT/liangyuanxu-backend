package com.wedding.packages.module;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("package_category")
public class PackageCategory {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private Integer sort;
    private LocalDateTime createdAt;
}
