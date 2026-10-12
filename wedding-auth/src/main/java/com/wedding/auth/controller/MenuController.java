package com.wedding.auth.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wedding.auth.mapper.MenuConfigMapper;
import com.wedding.auth.module.MenuConfig;
import com.wedding.common.BizException;
import com.wedding.common.Result;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单配置管理：管理员控制用户端(小程序)与商家端(后台)的菜单显示。
 * - 列表/树：按端(side)查询
 * - 新增/编辑/删除：管理员操作
 * - 公开接口 /public：供小程序与后台启动时拉取可见菜单
 */
@RestController
@RequestMapping("/api/auth/menu")
public class MenuController {

    private final MenuConfigMapper menuMapper;

    public MenuController(MenuConfigMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("menu-service ok");
    }

    /** 管理端：按端查询全部菜单（含隐藏），组装为树 */
    @GetMapping("/list")
    public Result<List<MenuConfig>> list(@RequestParam(defaultValue = "user") String side) {
        List<MenuConfig> all = menuMapper.selectList(Wrappers.<MenuConfig>lambdaQuery()
                .eq(MenuConfig::getSide, side)
                .orderByAsc(MenuConfig::getSort)
                .orderByAsc(MenuConfig::getId));
        return Result.ok(buildTree(all));
    }

    /** 管理端：扁平列表（便于表格/调试） */
    @GetMapping("/flat")
    public Result<List<MenuConfig>> flat(@RequestParam(defaultValue = "user") String side) {
        return Result.ok(menuMapper.selectList(Wrappers.<MenuConfig>lambdaQuery()
                .eq(MenuConfig::getSide, side)
                .orderByAsc(MenuConfig::getSort)
                .orderByAsc(MenuConfig::getId)));
    }

    /**
     * 公开：按端 + 角色拉取可见菜单树（供小程序 / 后台启动时使用）。
     * @param side  user / merchant
     * @param role  当前角色（可选）；不传则只按 visible+enabled 过滤
     */
    @GetMapping("/public")
    public Result<List<MenuConfig>> publicTree(@RequestParam(defaultValue = "user") String side,
                                               @RequestParam(required = false) String role) {
        List<MenuConfig> all = menuMapper.selectList(Wrappers.<MenuConfig>lambdaQuery()
                .eq(MenuConfig::getSide, side)
                .eq(MenuConfig::getEnabled, 1)
                .orderByAsc(MenuConfig::getSort)
                .orderByAsc(MenuConfig::getId));
        List<MenuConfig> visible = all.stream()
                .filter(m -> m.getVisible() != null && m.getVisible() == 1)
                .filter(m -> roleVisible(m.getRoles(), role))
                .collect(Collectors.toList());
        return Result.ok(buildTree(visible));
    }

    /** 角色是否可见：roles 为空表示全部可见；否则需包含当前角色（中英混用均可） */
    private boolean roleVisible(String roles, String role) {
        return com.wedding.common.RoleUtil.matches(roles, role);
    }

    @PostMapping
    public Result<MenuConfig> create(@RequestBody MenuConfig body) {
        if (!StringUtils.hasText(body.getSide())) {
            throw new BizException("缺少所属端 side");
        }
        if (!StringUtils.hasText(body.getCode())) {
            throw new BizException("缺少菜单标识 code");
        }
        if (!StringUtils.hasText(body.getName())) {
            throw new BizException("缺少菜单名称");
        }
        Long dup = menuMapper.selectCount(Wrappers.<MenuConfig>lambdaQuery()
                .eq(MenuConfig::getSide, body.getSide())
                .eq(MenuConfig::getCode, body.getCode()));
        if (dup != null && dup > 0) {
            throw new BizException("该端已存在相同标识 code：" + body.getCode());
        }
        MenuConfig m = new MenuConfig();
        m.setSide(body.getSide());
        m.setParentId(body.getParentId() == null ? 0 : body.getParentId());
        m.setCode(body.getCode().trim());
        m.setName(body.getName().trim());
        m.setIcon(body.getIcon() == null ? "" : body.getIcon());
        m.setPath(body.getPath() == null ? "" : body.getPath());
        m.setComponent(normalizeComponent(body.getComponent()));
        m.setRouteName(StringUtils.hasText(body.getRouteName()) ? body.getRouteName().trim() : body.getCode().trim());
        m.setRoles(body.getRoles() == null ? "" : body.getRoles());
        m.setVisible(body.getVisible() == null ? 1 : body.getVisible());
        m.setEnabled(body.getEnabled() == null ? 1 : body.getEnabled());
        m.setSort(body.getSort() == null ? 0 : body.getSort());
        menuMapper.insert(m);
        return Result.ok(m, "已创建");
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Integer id, @RequestBody MenuConfig body) {
        MenuConfig old = menuMapper.selectById(id);
        if (old == null) {
            throw new BizException("菜单不存在");
        }
        if (StringUtils.hasText(body.getCode()) && !body.getCode().equals(old.getCode())) {
            Long dup = menuMapper.selectCount(Wrappers.<MenuConfig>lambdaQuery()
                    .eq(MenuConfig::getSide, old.getSide())
                    .eq(MenuConfig::getCode, body.getCode())
                    .ne(MenuConfig::getId, id));
            if (dup != null && dup > 0) {
                throw new BizException("该端已存在相同标识 code：" + body.getCode());
            }
            old.setCode(body.getCode().trim());
        }
        if (body.getParentId() != null) {
            if (body.getParentId().equals(id)) {
                throw new BizException("父菜单不能是自己");
            }
            old.setParentId(body.getParentId());
        }
        if (body.getName() != null) old.setName(body.getName().trim());
        if (body.getIcon() != null) old.setIcon(body.getIcon());
        if (body.getPath() != null) old.setPath(body.getPath());
        if (body.getComponent() != null) old.setComponent(normalizeComponent(body.getComponent()));
        if (body.getRouteName() != null) old.setRouteName(body.getRouteName());
        if (body.getRoles() != null) old.setRoles(body.getRoles());
        if (body.getVisible() != null) old.setVisible(body.getVisible());
        if (body.getEnabled() != null) old.setEnabled(body.getEnabled());
        if (body.getSort() != null) old.setSort(body.getSort());
        menuMapper.updateById(old);
        return Result.ok(null, "已保存");
    }

    /** 切换可见性（快捷开关） */
    @PutMapping("/{id}/visible")
    public Result<Void> toggleVisible(@PathVariable Integer id, @RequestParam Integer visible) {
        MenuConfig old = menuMapper.selectById(id);
        if (old == null) {
            throw new BizException("菜单不存在");
        }
        old.setVisible(visible);
        menuMapper.updateById(old);
        return Result.ok(null, "已更新");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        MenuConfig old = menuMapper.selectById(id);
        if (old == null) {
            throw new BizException("菜单不存在");
        }
        Long children = menuMapper.selectCount(Wrappers.<MenuConfig>lambdaQuery()
                .eq(MenuConfig::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException("请先删除该菜单下的子菜单");
        }
        menuMapper.deleteById(id);
        return Result.ok(null, "已删除");
    }

    /**
     * 商家端动态路由：将 menu_config 转换为 vben 后端路由格式（RouteRecordStringComponent）。
     * 供后台在 backend 访问模式下生成左侧菜单与路由。
     * - 顶级目录（component=BasicLayout）带 children
     * - 叶子页面 component 为视图路径（如 wedding/stats/index）
     * - roles 非空时写入 meta.authority，由前端按权限过滤
     */
    @GetMapping("/routes")
    public Result<List<Map<String, Object>>> routes(@RequestParam(defaultValue = "merchant") String side) {
        List<MenuConfig> all = menuMapper.selectList(Wrappers.<MenuConfig>lambdaQuery()
                .eq(MenuConfig::getSide, side)
                .eq(MenuConfig::getEnabled, 1)
                .orderByAsc(MenuConfig::getSort)
                .orderByAsc(MenuConfig::getId));
        List<MenuConfig> visible = all.stream()
                .filter(m -> m.getVisible() != null && m.getVisible() == 1)
                .collect(Collectors.toList());
        List<MenuConfig> tree = buildTree(visible);
        List<Map<String, Object>> out = new ArrayList<>();
        for (MenuConfig m : tree) {
            Map<String, Object> r = toRoute(m);
            if (r != null) out.add(r);
        }
        return Result.ok(out);
    }

    /** 将 MenuConfig 转为 vben 路由节点（Map 形式）；path 为空且无子菜单的无效项返回 null */
    private Map<String, Object> toRoute(MenuConfig m) {
        boolean isDir = m.getChildren() != null && !m.getChildren().isEmpty();
        // 无效项：无 path 且无子菜单 → 跳过（避免生成无法跳转的空路由）
        if (!isDir && !StringUtils.hasText(m.getPath())) {
            return null;
        }
        Map<String, Object> route = new LinkedHashMap<>();
        // name：优先用 route_name（与前端静态路由 name 一致，便于 mixed 模式合并去重），否则用 code
        String routeName = StringUtils.hasText(m.getRouteName()) ? m.getRouteName() : m.getCode();
        route.put("name", routeName);
        route.put("path", m.getPath() == null ? "" : m.getPath());
        // 目录用 BasicLayout，叶子用页面组件路径
        String component = m.getComponent();
        if (isDir && (component == null || component.isEmpty())) {
            component = "BasicLayout";
        }
        route.put("component", component == null ? "" : component);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("title", m.getName());
        meta.put("icon", m.getIcon());
        meta.put("order", m.getSort());
        if (StringUtils.hasText(m.getRoles())) {
            List<String> auth = new ArrayList<>();
            for (String r : m.getRoles().split(",")) {
                if (StringUtils.hasText(r)) auth.add(r.trim());
            }
            meta.put("authority", auth);
        }
        route.put("meta", meta);
        if (isDir) {
            List<Map<String, Object>> children = new ArrayList<>();
            for (MenuConfig c : m.getChildren()) {
                Map<String, Object> child = toRoute(c);
                if (child != null) children.add(child);
            }
            // 子菜单全部无效时：若自身有有效 path/component，则降级为叶子页面；否则丢弃
            if (children.isEmpty()) {
                if (!StringUtils.hasText(m.getPath())) {
                    return null;
                }
                route.put("component", StringUtils.hasText(component) ? component : route.get("component"));
                return route;
            }
            route.put("children", children);
            // 目录重定向到第一个子菜单
            route.put("redirect", children.get(0).get("path"));
        }
        return route;
    }

    /** 归一化组件路径：去前缀 ../ ./ views/ 与后缀 .vue */
    private String normalizeComponent(String c) {
        if (c == null) return "";
        String s = c.trim();
        while (s.startsWith("../") || s.startsWith("./")) {
            s = s.startsWith("../") ? s.substring(3) : s.substring(2);
        }
        if (s.startsWith("views/")) s = s.substring(6);
        if (s.startsWith("/views/")) s = s.substring(7);
        if (s.startsWith("/")) s = s.substring(1);
        if (s.endsWith(".vue")) s = s.substring(0, s.length() - 4);
        return s;
    }

    /** 组装树：parentId=0 为根，其余挂到父节点 */
    private List<MenuConfig> buildTree(List<MenuConfig> all) {
        Map<Integer, MenuConfig> byId = new LinkedHashMap<>();
        for (MenuConfig m : all) {
            m.setChildren(new ArrayList<>());
            byId.put(m.getId(), m);
        }
        List<MenuConfig> roots = new ArrayList<>();
        for (MenuConfig m : all) {
            Integer pid = m.getParentId() == null ? 0 : m.getParentId();
            if (pid == 0 || !byId.containsKey(pid)) {
                roots.add(m);
            } else {
                byId.get(pid).getChildren().add(m);
            }
        }
        return roots;
    }
}
