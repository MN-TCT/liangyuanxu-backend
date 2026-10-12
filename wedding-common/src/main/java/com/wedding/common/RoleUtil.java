package com.wedding.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 角色工具：统一角色口径。
 *
 * <p>系统内存在历史遗留数据：部分表（sys_user / staff / staff_request /
 * staff_role_config）的角色字段写入的是中文名（如「摄影师」），
 * 而规范口径应为英文编码（如 {@code photographer}）。
 * 本工具提供双向归一化，保证菜单过滤、权限判断等场景不会因中英混用而失效。</p>
 */
public final class RoleUtil {

    /** 英文编码 -> 中文名 */
    private static final Map<String, String> CODE_TO_LABEL = new LinkedHashMap<>();
    /** 中文名 -> 英文编码 */
    private static final Map<String, String> LABEL_TO_CODE = new LinkedHashMap<>();

    static {
        register("admin", "管理员");
        register("wedding_company", "婚庆公司");
        register("makeup_artist", "跟妆师");
        register("photographer", "摄影师");
        register("cameraman", "摄像师");
        register("host", "主持人");
        register("lighting", "灯光师");
        register("customer", "普通用户(新人)");
    }

    private RoleUtil() {
    }

    private static void register(String code, String label) {
        CODE_TO_LABEL.put(code, label);
        LABEL_TO_CODE.put(label, code);
        // 兼容不带括号的写法
        LABEL_TO_CODE.putIfAbsent(label.replaceAll("[（(].*[)）]", ""), code);
    }

    /**
     * 归一化为英文编码：中文标签会转成对应编码；已是编码或未知值原样返回。
     */
    public static String toCode(String role) {
        if (role == null) {
            return null;
        }
        String r = role.trim();
        if (r.isEmpty()) {
            return r;
        }
        String hit = LABEL_TO_CODE.get(r);
        return hit != null ? hit : r;
    }

    /**
     * 归一化为中文名：英文编码会转成中文；已是中文或未知值原样返回。
     */
    public static String toLabel(String role) {
        if (role == null) {
            return null;
        }
        String r = role.trim();
        if (r.isEmpty()) {
            return r;
        }
        String hit = CODE_TO_LABEL.get(r);
        return hit != null ? hit : r;
    }

    /**
     * 判断角色是否匹配（中英混用均可）：用于菜单 roles 过滤等场景。
     *
     * @param roleList  配置中允许的角色列表（英文编码或中文，逗号分隔）
     * @param current   当前角色（英文编码或中文）
     */
    public static boolean matches(String roleList, String current) {
        if (roleList == null || roleList.isBlank()) {
            return true;
        }
        if (current == null || current.isBlank()) {
            return false;
        }
        String curCode = toCode(current);
        for (String r : roleList.split(",")) {
            String code = toCode(r);
            if (code.equalsIgnoreCase(curCode)) {
                return true;
            }
        }
        return false;
    }
}
