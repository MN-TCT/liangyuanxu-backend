package com.wedding.auth.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wedding.auth.mapper.RoleConfigMapper;
import com.wedding.auth.module.RoleConfig;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 技术职位配置表自动建表 + 平台默认职位模板(company_id=0)。
 * 每个商家首次查询职位时由接口自动克隆模板为其独有职位。
 * 数据库重启后自动补齐表结构，无需手动执行 SQL。
 */
@Component
public class RoleTableInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final RoleConfigMapper roleConfigMapper;

    public RoleTableInitializer(JdbcTemplate jdbcTemplate, RoleConfigMapper roleConfigMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.roleConfigMapper = roleConfigMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS staff_role_config ("
                + "id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,"
                + "company_id INT UNSIGNED NOT NULL DEFAULT 0,"
                + "role_code VARCHAR(50) NOT NULL,"
                + "role_name VARCHAR(50) NOT NULL,"
                + "sort INT DEFAULT 0,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "UNIQUE KEY uk_company_role (company_id, role_code)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        ensureCompanyColumn();
        seedTemplates();
    }

    /** 兼容旧表：补齐 company_id 列并将 role_code 唯一索引升级为 (company_id, role_code) 联合唯一 */
    private void ensureCompanyColumn() {
        Integer has = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'staff_role_config' AND COLUMN_NAME = 'company_id'",
                Integer.class);
        if (has != null && has > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE staff_role_config ADD COLUMN company_id INT UNSIGNED NOT NULL DEFAULT 0");
        try {
            jdbcTemplate.execute("DROP INDEX role_code ON staff_role_config");
        } catch (Exception ignored) {
            // 旧索引可能已被复用，忽略
        }
        try {
            jdbcTemplate.execute("ALTER TABLE staff_role_config ADD UNIQUE KEY uk_company_role (company_id, role_code)");
        } catch (Exception ignored) {
            // 联合唯一索引已存在
        }
    }

    /** 平台默认职位模板(company_id=0)，供各商家首次克隆 */
    private void seedTemplates() {
        Long cnt = roleConfigMapper.selectCount(Wrappers.<RoleConfig>lambdaQuery()
                .eq(RoleConfig::getCompanyId, 0));
        if (cnt != null && cnt > 0) {
            return;
        }
        List<RoleConfig> seeds = List.of(
                seed(0, "摄影师", "摄影师", 0),
                seed(0, "跟妆师", "跟妆师", 1),
                seed(0, "主持人", "主持人", 2),
                seed(0, "摄像师", "摄像师", 3),
                seed(0, "灯光师", "灯光师", 4));
        for (RoleConfig c : seeds) {
            roleConfigMapper.insert(c);
        }
    }

    private RoleConfig seed(int companyId, String code, String name, int sort) {
        RoleConfig c = new RoleConfig();
        c.setCompanyId(companyId);
        c.setRoleCode(code);
        c.setRoleName(name);
        c.setSort(sort);
        return c;
    }
}
