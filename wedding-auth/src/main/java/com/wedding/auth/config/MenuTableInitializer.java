package com.wedding.auth.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wedding.auth.mapper.MenuConfigMapper;
import com.wedding.auth.module.MenuConfig;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 菜单配置表自动建表 + 默认菜单种子。
 * 管理员可在「菜单管理」页控制用户端(小程序)与商家端(后台)的菜单显示、图标、排序与子菜单。
 */
@Component
public class MenuTableInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final MenuConfigMapper menuMapper;

    public MenuTableInitializer(JdbcTemplate jdbcTemplate, MenuConfigMapper menuMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.menuMapper = menuMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS menu_config ("
                + "id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,"
                + "side VARCHAR(20) NOT NULL DEFAULT 'user',"
                + "parent_id INT UNSIGNED NOT NULL DEFAULT 0,"
                + "code VARCHAR(64) NOT NULL,"
                + "name VARCHAR(50) NOT NULL,"
                + "icon VARCHAR(64) DEFAULT '',"
                + "path VARCHAR(200) DEFAULT '',"
                + "component VARCHAR(200) DEFAULT '',"
                + "route_name VARCHAR(64) DEFAULT '',"
                + "roles VARCHAR(200) DEFAULT '',"
                + "visible TINYINT NOT NULL DEFAULT 1,"
                + "enabled TINYINT NOT NULL DEFAULT 1,"
                + "sort INT DEFAULT 0,"
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "UNIQUE KEY uk_side_code (side, code),"
                + "KEY idx_side (side),"
                + "KEY idx_parent (parent_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        // 兼容旧库：补齐 component 列
        try {
            jdbcTemplate.execute("ALTER TABLE menu_config ADD COLUMN component VARCHAR(200) DEFAULT '' AFTER path");
        } catch (Exception ignore) {
            // 列已存在
        }
        // 兼容旧库：补齐 route_name 列
        try {
            jdbcTemplate.execute("ALTER TABLE menu_config ADD COLUMN route_name VARCHAR(64) DEFAULT '' AFTER component");
        } catch (Exception ignore) {
            // 列已存在
        }
        seedDefaults();
    }

    private void seedDefaults() {
        // ---------- 用户端（小程序） ----------
        // 底部 Dock
        upsert("user", 0, "dock_home", "首页", "🏠", "/pages/index/index", "", "", "", 1, 1, 0);
        upsert("user", 0, "dock_order", "订单", "🧾", "/pages/order/index", "", "", "", 1, 1, 1);
        upsert("user", 0, "dock_message", "消息", "💬", "/pages/message/index", "", "", "", 1, 1, 2);
        upsert("user", 0, "dock_profile", "我的", "👤", "/pages/profile/index", "", "", "", 1, 1, 3);
        // 个人中心功能宫格
        upsert("user", 0, "grid_order", "我的订单", "📋", "/pages/order/index", "", "", "", 1, 1, 10);
        upsert("user", 0, "grid_message", "咨询消息", "💬", "/pages/message/index", "", "", "", 1, 1, 11);
        upsert("user", 0, "grid_tasks", "我的任务", "🗂️", "/pages/my-tasks/index", "", "", "makeup_artist,photographer,host,cameraman,lighting", 1, 1, 12);
        upsert("user", 0, "grid_staff_apply", "申请商家职位", "🤝", "/pages/staff-apply/index", "", "", "customer", 1, 1, 13);
        upsert("user", 0, "grid_approve", "申请审核", "✅", "/pages/approve/index", "", "", "wedding_company", 1, 1, 14);
        upsert("user", 0, "grid_home", "返回首页", "🏠", "/pages/index/index", "", "", "", 1, 1, 15);

        // ---------- 商家端（管理后台） ----------
        // 顶级菜单（component 为页面组件路径；顶级目录用 BasicLayout）
        int mHome = upsert("merchant", 0, "m_home", "首页", "lucide:home", "/home", "wedding/home/index", "WeddingHome", "", 1, 1, 10);
        int mBiz = upsert("merchant", 0, "m_business", "经营业务", "lucide:store", "/business", "BasicLayout", "WeddingBusiness", "", 1, 1, 11);
        int mTeam = upsert("merchant", 0, "m_team", "客户与团队", "lucide:users", "/team", "BasicLayout", "WeddingTeam", "", 1, 1, 12);
        int mOps = upsert("merchant", 0, "m_ops", "平台运营", "lucide:settings", "/ops", "BasicLayout", "WeddingOps", "", 1, 1, 13);
        int mPlat = upsert("merchant", 0, "m_platform", "平台管理", "lucide:shield", "/platform", "BasicLayout", "WeddingPlatform", "admin", 1, 1, 14);
        upsert("merchant", 0, "m_merchant_apply", "商家入驻", "lucide:store", "/wedding/merchant/apply", "wedding/merchant/apply/index", "WeddingMerchantApplySelf", "", 1, 1, 15);

        // 经营业务子菜单
        upsert("merchant", mBiz, "m_package", "套餐管理", "lucide:camera", "/wedding/package", "wedding/package/index", "WeddingPackage", "", 1, 1, 1);
        upsert("merchant", mBiz, "m_booking", "订单管理", "lucide:calendar-check", "/wedding/booking", "wedding/booking/index", "WeddingBooking", "", 1, 1, 2);
        upsert("merchant", mBiz, "m_video", "视频管理", "lucide:video", "/wedding/video", "wedding/video/index", "WeddingVideo", "", 1, 1, 3);
        upsert("merchant", mBiz, "m_store", "用户端浏览", "lucide:eye", "/wedding/store", "wedding/store/index", "WeddingStore", "", 1, 1, 4);

        // 客户与团队子菜单
        upsert("merchant", mTeam, "m_staff", "人才管理", "lucide:briefcase", "/wedding/staff", "wedding/staff/index", "WeddingStaff", "", 1, 1, 2);
        upsert("merchant", mTeam, "m_message", "咨询消息", "lucide:message-circle", "/wedding/message", "wedding/message/index", "WeddingMessage", "", 1, 1, 3);
        upsert("merchant", mTeam, "m_approve", "申请审核", "lucide:shield-check", "/wedding/approve", "wedding/approve/index", "WeddingApprove", "", 1, 1, 4);

        // 平台运营子菜单
        upsert("merchant", mOps, "m_config", "基础配置", "lucide:sliders-horizontal", "/wedding/config", "wedding/config/index", "WeddingConfig", "", 1, 1, 2);

        // 平台管理子菜单（仅管理员）
        upsert("merchant", mPlat, "m_home_pin", "首页推荐", "lucide:star", "/wedding/home-pin", "wedding/home-pin/index", "WeddingHomePin", "admin", 1, 1, 1);
        upsert("merchant", mPlat, "m_user", "用户管理", "lucide:user", "/wedding/user", "wedding/user/index", "WeddingUser", "admin", 1, 1, 2);
        upsert("merchant", mPlat, "m_merchant_apply_admin", "商家入驻申请", "lucide:shield-check", "/wedding/merchant-apply", "wedding/merchant/index", "WeddingMerchantApply", "admin", 1, 1, 3);
        upsert("merchant", mPlat, "m_stats", "数据统计", "lucide:bar-chart-3", "/wedding/stats", "wedding/stats/index", "WeddingStats", "admin", 1, 1, 4);
        upsert("merchant", mPlat, "m_menu", "菜单管理", "lucide:menu", "/wedding/menu", "wedding/menu/index", "WeddingMenu", "admin", 1, 1, 5);
    }

    /**
     * 幂等写入：不存在则插入，返回记录 id。
     * 不覆盖已有配置（保留管理员在「菜单管理」中的修改）。
     */
    private int upsert(String side, int parentId, String code, String name, String icon, String path,
                       String component, String routeName, String roles, int visible, int enabled, int sort) {
        MenuConfig exist = menuMapper.selectOne(Wrappers.<MenuConfig>lambdaQuery()
                .eq(MenuConfig::getSide, side)
                .eq(MenuConfig::getCode, code)
                .last("LIMIT 1"));
        if (exist != null) {
            // 补齐历史数据缺失的 component / route_name
            boolean dirty = false;
            if ((exist.getComponent() == null || exist.getComponent().isEmpty())
                    && component != null && !component.isEmpty()) {
                exist.setComponent(component);
                dirty = true;
            }
            if ((exist.getRouteName() == null || exist.getRouteName().isEmpty())
                    && routeName != null && !routeName.isEmpty()) {
                exist.setRouteName(routeName);
                dirty = true;
            }
            if (dirty) {
                menuMapper.updateById(exist);
            }
            return exist.getId();
        }
        MenuConfig m = new MenuConfig();
        m.setSide(side);
        m.setParentId(parentId);
        m.setCode(code);
        m.setName(name);
        m.setIcon(icon);
        m.setPath(path);
        m.setComponent(component);
        m.setRouteName(routeName);
        m.setRoles(roles);
        m.setVisible(visible);
        m.setEnabled(enabled);
        m.setSort(sort);
        menuMapper.insert(m);
        return m.getId() == null ? 0 : m.getId();
    }
}
