package com.wedding.auth.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 商家入驻申请表自动建表 + 字段补齐，服务启动自动执行，无需手动 SQL。
 */
@Component
public class MerchantTableInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public MerchantTableInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS merchant_request ("
                + "id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,"
                + "user_id INT UNSIGNED NOT NULL,"
                + "shop_name VARCHAR(50) NOT NULL,"
                + "contact_name VARCHAR(30) DEFAULT '',"
                + "phone VARCHAR(20) DEFAULT '',"
                + "address VARCHAR(100) DEFAULT '',"
                + "business_type VARCHAR(100) DEFAULT '',"
                + "license_no VARCHAR(50) DEFAULT '',"
                + "introduction VARCHAR(500) DEFAULT '',"
                + "status TINYINT NOT NULL DEFAULT 0,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "reviewed_at DATETIME NULL,"
                + "KEY idx_user (user_id),"
                + "KEY idx_status (status)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        ensureColumns();
        ensureSysUserColumns();
    }

    /** 兼容旧表：补齐新增列 */
    private void ensureColumns() {
        ensureColumn("merchant_request", "contact_name", "VARCHAR(30) DEFAULT ''");
        ensureColumn("merchant_request", "address", "VARCHAR(100) DEFAULT ''");
        ensureColumn("merchant_request", "business_type", "VARCHAR(100) DEFAULT ''");
        ensureColumn("merchant_request", "license_no", "VARCHAR(50) DEFAULT ''");
        ensureColumn("merchant_request", "license_img", "VARCHAR(500) DEFAULT ''");
    }

    /** 商家账号(sys_user) 补齐商家资料列 */
    private void ensureSysUserColumns() {
        ensureColumn("sys_user", "contact_name", "VARCHAR(30) DEFAULT ''");
        ensureColumn("sys_user", "address", "VARCHAR(100) DEFAULT ''");
        ensureColumn("sys_user", "business_type", "VARCHAR(100) DEFAULT ''");
        ensureColumn("sys_user", "license_no", "VARCHAR(50) DEFAULT ''");
        ensureColumn("sys_user", "license_img", "VARCHAR(500) DEFAULT ''");
        // 首页商家展示开关：1=在首页商家栏展示，0=不展示（默认全部展示，保持旧行为）
        ensureColumn("sys_user", "home_show", "TINYINT NOT NULL DEFAULT 1");
        ensureColumn("sys_user", "home_sort", "INT NOT NULL DEFAULT 0");
    }

    private void ensureColumn(String table, String col, String ddl) {
        Integer has = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table + "' AND COLUMN_NAME = '"
                        + col + "'",
                Integer.class);
        if (has != null && has > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + col + " " + ddl);
    }
}
