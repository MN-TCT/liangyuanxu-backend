package com.wedding.packages.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wedding.common.BizException;
import com.wedding.common.Result;
import com.wedding.packages.mapper.OptionConfigMapper;
import com.wedding.packages.module.OptionConfig;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 下拉选项配置 CRUD：拍摄地点(location) / 拍摄天数(days) / 包含服务(service)
 * 网关已把 /api/package/** 转发到本服务，无需新增网关路由
 */
@RestController
@RequestMapping("/api/package/options")
public class OptionConfigController {

    private final OptionConfigMapper mapper;

    public OptionConfigController(OptionConfigMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public Result<List<OptionConfig>> list(@RequestParam String category) {
        if (!StringUtils.hasText(category)) {
            throw new BizException("缺少类别");
        }
        List<OptionConfig> list = mapper.selectList(
                Wrappers.<OptionConfig>lambdaQuery()
                        .eq(OptionConfig::getCategory, category)
                        .orderByAsc(OptionConfig::getSort)
                        .orderByAsc(OptionConfig::getId));
        return Result.ok(list);
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody OptionConfig body) {
        if (!StringUtils.hasText(body.getCategory()) || !StringUtils.hasText(body.getValue())) {
            throw new BizException("类别和选项值不能为空");
        }
        checkLen(body.getCategory(), 32, "类别");
        checkLen(body.getValue(), 20, "选项值");
        checkLen(body.getLabel(), 20, "选项名称");
        if (!StringUtils.hasText(body.getLabel())) {
            body.setLabel(body.getValue());
        }
        if (body.getSort() == null) {
            body.setSort(0);
        }
        mapper.insert(body);
        Map<String, Object> data = new HashMap<>();
        data.put("id", body.getId());
        return Result.ok(data, "选项添加成功");
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Integer id, @RequestBody OptionConfig body) {
        checkLen(body.getCategory(), 32, "类别");
        checkLen(body.getValue(), 20, "选项值");
        checkLen(body.getLabel(), 20, "选项名称");
        body.setId(id);
        mapper.updateById(body);
        return Result.ok(null, "选项更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        mapper.deleteById(id);
        return Result.ok(null, "选项删除成功");
    }

    private void checkLen(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new BizException(field + "不能超过 " + max + " 个字符");
        }
    }
}
