package com.wedding.auth.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 职位配置表结构自动迁移：新增「服务费」与「费用是否对技术人员可见」字段。
 * 幂等：列已存在则忽略。
 */
@Component
public class RoleConfigTableInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public RoleConfigTableInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        addColumn("fee", "DECIMAL(10,2) DEFAULT 0");
        addColumn("fee_visible", "TINYINT DEFAULT 1");
    }

    private void addColumn(String name, String type) {
        try {
            jdbcTemplate.execute("ALTER TABLE staff_role_config ADD COLUMN " + name + " " + type);
        } catch (Exception ignore) {
            // 列已存在
        }
    }
}
