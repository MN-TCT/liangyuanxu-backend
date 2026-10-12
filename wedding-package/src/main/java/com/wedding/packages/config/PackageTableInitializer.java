package com.wedding.packages.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 套餐表结构自动迁移（幂等）。
 * 新增：cover_urls —— 多张封面图（JSON 数组字符串），兼容历史单封面 cover_url。
 */
@Component
public class PackageTableInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PackageTableInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public PackageTableInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        addColumnIfMissing("cover_urls", "TEXT NULL COMMENT '多张封面图 JSON 数组' AFTER cover_url");
    }

    private void addColumnIfMissing(String column, String definition) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wedding_package' AND COLUMN_NAME = ?",
                    Integer.class, column);
            if (cnt != null && cnt > 0) {
                log.info("[PackageTableInitializer] 列 {} 已存在，跳过", column);
                return;
            }
            jdbcTemplate.execute("ALTER TABLE wedding_package ADD COLUMN " + column + " " + definition);
            log.info("[PackageTableInitializer] 已新增列 {}", column);
        } catch (Exception e) {
            log.warn("[PackageTableInitializer] 新增列 {} 失败：{}", column, e.getMessage());
        }
    }
}
