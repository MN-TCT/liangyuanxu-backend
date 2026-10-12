package com.wedding.auth.controller;

import com.wedding.common.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台数据总览：聚合用户端(小程序)与商家端(后台)的核心指标。
 * 仅管理员可见（前端路由已限制 authority=admin）。
 */
@RestController
@RequestMapping("/api/auth/stats")
public class StatsController {

    private final JdbcTemplate jdbc;

    public StatsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("stats-service ok");
    }

    /** 平台总览：用户端 + 商家端核心指标 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> data = new LinkedHashMap<>();

        // ============ 用户端（小程序） ============
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("totalUsers", count("SELECT COUNT(*) FROM wedding_auth.sys_user"));
        user.put("customers", count("SELECT COUNT(*) FROM wedding_auth.sys_user WHERE role='customer'"));
        user.put("newToday", count(
                "SELECT COUNT(*) FROM wedding_auth.sys_user WHERE DATE(created_at)=CURDATE()"));
        user.put("newWeek", count(
                "SELECT COUNT(*) FROM wedding_auth.sys_user WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)"));
        // 用户端浏览/咨询：会话数 & 消息数
        user.put("conversations", count("SELECT COUNT(*) FROM wedding_message.conversation"));
        user.put("messages", count("SELECT COUNT(*) FROM wedding_message.chat_message"));
        // 用户端下单
        user.put("bookings", count("SELECT COUNT(*) FROM wedding_booking.booking_order"));
        data.put("user", user);

        // ============ 商家端（后台） ============
        Map<String, Object> merchant = new LinkedHashMap<>();
        merchant.put("totalMerchants", count(
                "SELECT COUNT(*) FROM wedding_auth.sys_user WHERE role='wedding_company'"));
        merchant.put("activeMerchants", count(
                "SELECT COUNT(DISTINCT company_id) FROM wedding_package.wedding_package WHERE status=1 AND company_id IS NOT NULL"));
        merchant.put("totalPackages", count("SELECT COUNT(*) FROM wedding_package.wedding_package"));
        merchant.put("onlinePackages", count(
                "SELECT COUNT(*) FROM wedding_package.wedding_package WHERE status=1"));
        merchant.put("totalVideos", count("SELECT COUNT(*) FROM wedding_video.wedding_video"));
        merchant.put("totalOrders", count("SELECT COUNT(*) FROM wedding_booking.booking_order"));
        merchant.put("pendingOrders", count(
                "SELECT COUNT(*) FROM wedding_booking.booking_order WHERE status=0"));
        merchant.put("doneOrders", count(
                "SELECT COUNT(*) FROM wedding_booking.booking_order WHERE status=2"));
        // 待审核：商家入驻 + 员工申请
        merchant.put("pendingMerchantApplies", count(
                "SELECT COUNT(*) FROM wedding_auth.merchant_request WHERE status=0"));
        merchant.put("pendingStaffApplies", count(
                "SELECT COUNT(*) FROM wedding_auth.staff_request WHERE status=0"));
        merchant.put("totalStaff", count("SELECT COUNT(*) FROM wedding_auth.staff"));
        data.put("merchant", merchant);

        // ============ 角色分布 ============
        List<Map<String, Object>> roles = jdbc.queryForList(
                "SELECT role, COUNT(*) AS cnt FROM wedding_auth.sys_user GROUP BY role ORDER BY cnt DESC");
        List<Map<String, Object>> roleList = new ArrayList<>();
        for (Map<String, Object> r : roles) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", r.get("role") == null ? "" : String.valueOf(r.get("role")));
            m.put("count", r.get("cnt"));
            roleList.add(m);
        }
        data.put("roles", roleList);

        // ============ 近 7 天新增用户趋势 ============
        List<Map<String, Object>> trend = jdbc.queryForList(
                "SELECT DATE_FORMAT(created_at,'%m-%d') AS day, COUNT(*) AS cnt " +
                "FROM wedding_auth.sys_user " +
                "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
                "GROUP BY DATE_FORMAT(created_at,'%m-%d') ORDER BY MIN(created_at)");
        List<Map<String, Object>> trendList = new ArrayList<>();
        for (Map<String, Object> t : trend) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day", t.get("day"));
            m.put("count", t.get("cnt"));
            trendList.add(m);
        }
        data.put("userTrend", trendList);

        // ============ 商家套餐排行（Top 8） ============
        List<Map<String, Object>> top = jdbc.queryForList(
                "SELECT u.shop_name AS shop, COUNT(p.id) AS cnt " +
                "FROM wedding_package.wedding_package p " +
                "JOIN wedding_auth.sys_user u ON u.id = p.company_id " +
                "WHERE p.status=1 GROUP BY p.company_id, u.shop_name ORDER BY cnt DESC LIMIT 8");
        List<Map<String, Object>> topList = new ArrayList<>();
        for (Map<String, Object> t : top) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("shop", t.get("shop") == null ? "" : String.valueOf(t.get("shop")));
            m.put("count", t.get("cnt"));
            topList.add(m);
        }
        data.put("topMerchants", topList);

        return Result.ok(data);
    }

    private long count(String sql) {
        try {
            Long v = jdbc.queryForObject(sql, Long.class);
            return v == null ? 0L : v;
        } catch (Exception e) {
            return 0L;
        }
    }
}
