package com.wedding.packages.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wedding.packages.module.WeddingPackage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PackageMapper extends BaseMapper<WeddingPackage> {
}
