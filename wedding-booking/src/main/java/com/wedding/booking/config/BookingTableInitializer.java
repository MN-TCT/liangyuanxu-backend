package com.wedding.booking.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单表结构自动迁移：为「商家自定义创建订单」补齐业务字段。
 * 幂等：列已存在则忽略。
 */
@Component
public class BookingTableInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public BookingTableInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 确保表存在（首次部署兜底）
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS booking_order ("
                + "id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,"
                + "customer_name VARCHAR(50),"
                + "phone VARCHAR(20),"
                + "package_id INT,"
                + "package_name VARCHAR(100),"
                + "plan_date DATE,"
                + "status TINYINT DEFAULT 0,"
                + "remark VARCHAR(500),"
                + "company_id INT,"
                + "staff_id INT,"
                + "staff_name VARCHAR(50),"
                + "staff_role VARCHAR(50),"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        addColumn("package_content", "TEXT");
        addColumn("dress_count", "INT DEFAULT 0");
        addColumn("shoot_location", "VARCHAR(200) DEFAULT ''");
        addColumn("scene_type", "VARCHAR(20) DEFAULT ''");
        addColumn("price", "DECIMAL(10,2) DEFAULT 0");
        addColumn("dresses", "TEXT");
        addColumn("source", "TINYINT DEFAULT 0");
    }

    private void addColumn(String name, String type) {
        try {
            jdbcTemplate.execute("ALTER TABLE booking_order ADD COLUMN " + name + " " + type);
        } catch (Exception ignore) {
            // 列已存在
        }
    }
}
