package com.wedding.auth.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wedding.auth.mapper.RoleConfigMapper;
import com.wedding.auth.mapper.StaffMapper;
import com.wedding.auth.mapper.StaffRequestMapper;
import com.wedding.auth.mapper.SysUserMapper;
import com.wedding.auth.module.RoleConfig;
import com.wedding.auth.module.Staff;
import com.wedding.auth.module.StaffRequest;
import com.wedding.auth.module.SysUser;
import com.wedding.common.BizException;
import com.wedding.common.JwtUtil;
import com.wedding.common.PageQuery;
import com.wedding.common.Result;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/staff")
public class StaffController {

    private final SysUserMapper userMapper;
    private final StaffMapper staffMapper;
    private final StaffRequestMapper staffRequestMapper;
    private final RoleConfigMapper roleConfigMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 历史英文职位码 -> 中文名（兼容旧数据，新职位一律以中文名作为 code 存库） */
    private static final Map<String, String> LEGACY_ROLE_LABELS = new LinkedHashMap<>();

    static {
        LEGACY_ROLE_LABELS.put("photographer", "摄影师");
        LEGACY_ROLE_LABELS.put("makeup_artist", "跟妆师");
        LEGACY_ROLE_LABELS.put("host", "主持人");
        LEGACY_ROLE_LABELS.put("cameraman", "摄像师");
        LEGACY_ROLE_LABELS.put("lighting", "灯光师");
    }

    public StaffController(SysUserMapper userMapper, StaffMapper staffMapper, StaffRequestMapper staffRequestMapper, RoleConfigMapper roleConfigMapper) {
        this.userMapper = userMapper;
        this.staffMapper = staffMapper;
        this.staffRequestMapper = staffRequestMapper;
        this.roleConfigMapper = roleConfigMapper;
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("staff-service ok");
    }

    /** 当前商家自己的技术职位列表（首次自动克隆平台模板） */
    @GetMapping("/roles")
    public Result<List<Map<String, Object>>> roles(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Integer companyId = currentUserId(authHeader);
        return Result.ok(roleOptions(companyId));
    }

    /** 技术人员：查看某商家开放的职位（小程序申请职位时选择用） */
    @GetMapping("/company/{companyId}/roles")
    public Result<List<Map<String, Object>>> companyRoles(@PathVariable Integer companyId) {
        return Result.ok(roleOptions(companyId));
    }

    /** 新增职位（当前商家自己的） */
    @PostMapping("/roles")
    public Result<Map<String, Object>> createRole(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> body) {
        Integer companyId = currentUserId(authHeader);
        String code = body.get("code") == null ? "" : body.get("code").trim();
        String name = body.get("name") == null ? "" : body.get("name").trim();
        if (code.isEmpty() || name.isEmpty()) {
            throw new BizException(400, "职位名称不能为空");
        }
        if (code.length() > 20 || name.length() > 20) {
            throw new BizException(400, "职位名称不能超过 20 字符");
        }
        Long exists = roleConfigMapper.selectCount(Wrappers.<RoleConfig>lambdaQuery()
                .eq(RoleConfig::getCompanyId, companyId).eq(RoleConfig::getRoleCode, code));
        if (exists != null && exists > 0) {
            throw new BizException(400, "职位已存在");
        }
        RoleConfig c = new RoleConfig();
        c.setCompanyId(companyId);
        c.setRoleCode(code);
        c.setRoleName(name);
        c.setSort(0);
        roleConfigMapper.insert(c);
        Map<String, Object> data = new HashMap<>();
        data.put("id", c.getId());
        data.put("code", code);
        data.put("name", name);
        return Result.ok(data, "职位已添加");
    }

    /** 更新职位（费用/费用可见性/名称），当前商家自己的 */
    @PutMapping("/roles/{code}")
    public Result<Void> updateRole(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String code,
            @RequestBody Map<String, Object> body) {
        Integer companyId = currentUserId(authHeader);
        RoleConfig c = roleConfigMapper.selectOne(Wrappers.<RoleConfig>lambdaQuery()
                .eq(RoleConfig::getCompanyId, companyId).eq(RoleConfig::getRoleCode, code));
        if (c == null) {
            throw new BizException(404, "职位不存在");
        }
        if (body.get("name") != null) {
            String name = String.valueOf(body.get("name")).trim();
            if (name.isEmpty() || name.length() > 20) {
                throw new BizException(400, "职位名称需为 1~20 个字符");
            }
            c.setRoleName(name);
        }
        if (body.get("fee") != null) {
            String feeStr = String.valueOf(body.get("fee")).trim();
            if (feeStr.isEmpty()) {
                c.setFee(java.math.BigDecimal.ZERO);
            } else {
                java.math.BigDecimal fee;
                try {
                    fee = new java.math.BigDecimal(feeStr);
                } catch (Exception e) {
                    throw new BizException(400, "费用格式不正确");
                }
                if (fee.compareTo(java.math.BigDecimal.ZERO) < 0) {
                    throw new BizException(400, "费用不能为负数");
                }
                if (fee.compareTo(new java.math.BigDecimal("99999999")) > 0) {
                    throw new BizException(400, "费用超出可录入范围");
                }
                c.setFee(fee);
            }
        }
        if (body.get("feeVisible") != null) {
            c.setFeeVisible("0".equals(String.valueOf(body.get("feeVisible"))) ? 0 : 1);
        }
        roleConfigMapper.updateById(c);
        return Result.ok(null, "职位已更新");
    }

    /** 删除职位（当前商家自己的） */
    @DeleteMapping("/roles/{code}")
    public Result<Void> deleteRole(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String code) {
        Integer companyId = currentUserId(authHeader);
        roleConfigMapper.delete(Wrappers.<RoleConfig>lambdaQuery()
                .eq(RoleConfig::getCompanyId, companyId).eq(RoleConfig::getRoleCode, code));
        return Result.ok(null, "职位已删除");
    }

    /** 职位是否存在（指定商家） */
    private boolean roleExists(String role, Integer companyId) {
        if (role == null || role.isEmpty()) {
            return false;
        }
        return roleConfigMapper.selectCount(Wrappers.<RoleConfig>lambdaQuery()
                .eq(RoleConfig::getCompanyId, companyId).eq(RoleConfig::getRoleCode, role)) > 0;
    }

    /** 职位名称：按商家查职位配置，其次历史英文映射，最后原码 */
    private String roleLabel(String role, Integer companyId) {
        if (role == null || role.isEmpty()) {
            return role;
        }
        RoleConfig c = roleConfigMapper.selectOne(Wrappers.<RoleConfig>lambdaQuery()
                .eq(RoleConfig::getCompanyId, companyId).eq(RoleConfig::getRoleCode, role));
        if (c != null) {
            return c.getRoleName();
        }
        return LEGACY_ROLE_LABELS.getOrDefault(role, role);
    }

    /** 返回某商家的职位选项(value=code,label=name)；每次查询幂等补齐缺失的平台模板，保证默认数据完整且不重复 */
    private List<Map<String, Object>> roleOptions(Integer companyId) {
        List<RoleConfig> tpl = roleConfigMapper.selectList(Wrappers.<RoleConfig>lambdaQuery()
                .eq(RoleConfig::getCompanyId, 0));
        for (RoleConfig t : tpl) {
            Long exists = roleConfigMapper.selectCount(Wrappers.<RoleConfig>lambdaQuery()
                    .eq(RoleConfig::getCompanyId, companyId)
                    .eq(RoleConfig::getRoleCode, t.getRoleCode()));
            if (exists == null || exists == 0) {
                RoleConfig c = new RoleConfig();
                c.setCompanyId(companyId);
                c.setRoleCode(t.getRoleCode());
                c.setRoleName(t.getRoleName());
                c.setSort(t.getSort());
                roleConfigMapper.insert(c);
            }
        }
        List<RoleConfig> rows = roleConfigMapper.selectList(Wrappers.<RoleConfig>lambdaQuery()
                .eq(RoleConfig::getCompanyId, companyId)
                .orderByAsc(RoleConfig::getSort).orderByAsc(RoleConfig::getId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (RoleConfig c : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("value", c.getRoleCode());
            m.put("label", c.getRoleName());
            m.put("fee", c.getFee());
            m.put("feeVisible", c.getFeeVisible() == null ? 1 : c.getFeeVisible());
            list.add(m);
        }
        return list;
    }

    /** 从 JWT 解析当前登录用户 id（作为商家 company_id） */
    private Integer currentUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BizException(401, "未登录或登录已过期");
        }
        try {
            Claims claims = JwtUtil.parseToken(authHeader.substring(7));
            Integer id = claims.get("id", Integer.class);
            if (id == null) {
                throw new BizException(401, "未登录或登录已过期");
            }
            return id;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(401, "登录状态已失效，请重新登录");
        }
    }

    /** 员工列表（当前登录商家名下的员工） */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer pageSize,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword) {
        Integer companyId = currentUserId(authHeader);
        boolean hasRole = role != null && !role.isEmpty();
        boolean hasKw = keyword != null && !keyword.isEmpty();
        page = PageQuery.normalizePage(page);
        pageSize = PageQuery.normalizePageSize(pageSize);
        Long total = staffMapper.selectCount(Wrappers.<Staff>lambdaQuery()
                .eq(Staff::getCompanyId, companyId)
                .eq(hasRole, Staff::getRole, role));
        List<Staff> rows = staffMapper.selectList(Wrappers.<Staff>lambdaQuery()
                .eq(Staff::getCompanyId, companyId)
                .eq(hasRole, Staff::getRole, role)
                .orderByDesc(Staff::getId)
                .last("limit " + PageQuery.offset(page, pageSize) + "," + pageSize));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Staff s : rows) {
            SysUser u = userMapper.selectById(s.getUserId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("staffId", s.getUserId());
            m.put("companyId", s.getCompanyId());
            m.put("role", s.getRole());
            m.put("roleLabel", roleLabel(s.getRole(), companyId));
            m.put("status", s.getStatus());
            m.put("nickname", u == null ? "" : u.getNickname());
            m.put("username", u == null ? "" : u.getUsername());
            m.put("phone", u == null ? "" : u.getPhone());
            m.put("createdAt", s.getCreatedAt());
            list.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        return Result.ok(data);
    }

    /** 通过手机号添加技术用户为员工 */
    @PostMapping
    public Result<Map<String, Object>> create(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> body) {
        Integer companyId = currentUserId(authHeader);
        String phone = body.get("phone");
        String role = body.get("role");
        if (phone == null || phone.isEmpty()) {
            throw new BizException("手机号不能为空");
        }
        if (!roleExists(role, companyId)) {
            throw new BizException(400, "请选择有效的职位");
        }
        String nickname = body.getOrDefault("nickname", "");
        // 按手机号查找用户，必须已注册才能添加
        SysUser u = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getPhone, phone));
        if (u == null) {
            throw new BizException(400, "该手机号未注册，请先让技术人员在小程序注册账号");
        } else {
            // 复用已有用户，确保为技术角色且启用
            if (!roleExists(u.getRole(), companyId)) {
                u.setRole(role);
            }
            if (u.getStatus() == null || u.getStatus() != 1) {
                u.setStatus(1);
            }
            userMapper.updateById(u);
        }
        // 建立商家-员工关联
        Staff exist = staffMapper.selectOne(Wrappers.<Staff>lambdaQuery()
                .eq(Staff::getCompanyId, companyId).eq(Staff::getUserId, u.getId()));
        if (exist == null) {
            Staff s = new Staff();
            s.setCompanyId(companyId);
            s.setUserId(u.getId());
            s.setRole(role);
            s.setStatus(1);
            staffMapper.insert(s);
        } else {
            exist.setRole(role);
            exist.setStatus(1);
            staffMapper.updateById(exist);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", u.getId());
        data.put("phone", phone);
        data.put("role", role);
        return Result.ok(data, "员工添加成功");
    }

    /** 停用/启用员工 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Integer id,
            @RequestParam Integer status) {
        Integer companyId = currentUserId(authHeader);
        Staff s = staffMapper.selectOne(Wrappers.<Staff>lambdaQuery()
                .eq(Staff::getId, id).eq(Staff::getCompanyId, companyId));
        if (s == null) {
            throw new BizException(404, "员工不存在");
        }
        s.setStatus(status == null || status == 1 ? 1 : 0);
        staffMapper.updateById(s);
        return Result.ok(null, s.getStatus() == 1 ? "已启用" : "已停用");
    }

    /** 删除员工（解除关联） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Integer id) {
        Integer companyId = currentUserId(authHeader);
        staffMapper.delete(Wrappers.<Staff>lambdaQuery().eq(Staff::getId, id).eq(Staff::getCompanyId, companyId));
        return Result.ok(null, "已移除员工");
    }

    /** 订单选员工候选：当前商家下指定职位的在职员工 */
    @GetMapping("/candidates")
    public Result<List<Map<String, Object>>> candidates(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) String role) {
        Integer companyId = currentUserId(authHeader);
        List<Staff> rows = staffMapper.selectList(Wrappers.<Staff>lambdaQuery()
                .eq(Staff::getCompanyId, companyId)
                .eq(Staff::getStatus, 1)
                .eq(role != null && !role.isEmpty(), Staff::getRole, role)
                .orderByDesc(Staff::getId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Staff s : rows) {
            SysUser u = userMapper.selectById(s.getUserId());
            Map<String, Object> m = new HashMap<>();
            m.put("staffId", s.getUserId());
            m.put("role", s.getRole());
            m.put("roleLabel", roleLabel(s.getRole(), companyId));
            m.put("nickname", u == null ? "" : u.getNickname());
            m.put("phone", u == null ? "" : u.getPhone());
            list.add(m);
        }
        return Result.ok(list);
    }

    /** 当前登录技术人员：我绑定的商家列表（含职位） */
    @GetMapping("/my-companies")
    public Result<List<Map<String, Object>>> myCompanies(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Integer userId = currentUserId(authHeader);
        List<Staff> rows = staffMapper.selectList(Wrappers.<Staff>lambdaQuery()
                .eq(Staff::getUserId, userId)
                .eq(Staff::getStatus, 1)
                .orderByDesc(Staff::getId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Staff s : rows) {
            SysUser company = userMapper.selectById(s.getCompanyId());
            Map<String, Object> m = new HashMap<>();
            m.put("staffId", s.getId());
            m.put("companyId", s.getCompanyId());
            m.put("companyName", company == null ? "" : (StringUtils.hasText(company.getShopName()) ? company.getShopName() : company.getNickname()));
            m.put("companyAvatar", company == null ? "" : (company.getAvatar() == null ? "" : company.getAvatar()));
            m.put("role", s.getRole());
            m.put("roleLabel", roleLabel(s.getRole(), s.getCompanyId()));
            list.add(m);
        }
        return Result.ok(list);
    }
    // ==================== 技术人员申请加入婚庆公司 ====================

    /** 婚庆公司列表（供技术人员选择申请加入） */
    @GetMapping("/companies")
    public Result<List<Map<String, Object>>> companies() {
        List<SysUser> rows = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .in(SysUser::getRole, "wedding_company", "admin").eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (SysUser u : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("companyId", u.getId());
            // 商家显示/搜索优先店铺名称，其次昵称
            m.put("name", StringUtils.hasText(u.getShopName()) ? u.getShopName() : u.getNickname());
            m.put("shopName", u.getShopName());
            m.put("username", u.getUsername());
            m.put("phone", u.getPhone());
            list.add(m);
        }
        return Result.ok(list);
    }

    /** 技术人员提交申请加入婚庆公司 */
    @PostMapping("/apply")
    public Result<Map<String, Object>> apply(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> body) {
        Integer userId = currentUserId(authHeader);
        Integer companyId = body.get("companyId") == null ? null
                : Integer.valueOf(String.valueOf(body.get("companyId")));
        String role = body.get("role") == null ? "" : String.valueOf(body.get("role"));
        if (companyId == null) {
            throw new BizException("请选择要加入的婚庆公司");
        }
        if (!roleExists(role, companyId)) {
            throw new BizException(400, "请选择有效的职位");
        }
        SysUser company = userMapper.selectById(companyId);
        if (company == null || (!"wedding_company".equals(company.getRole()) && !"admin".equals(company.getRole()))) {
            throw new BizException(400, "目标婚庆公司不存在");
        }
        Long pending = staffRequestMapper.selectCount(Wrappers.<StaffRequest>lambdaQuery()
                .eq(StaffRequest::getUserId, userId).eq(StaffRequest::getCompanyId, companyId)
                .eq(StaffRequest::getStatus, 0));
        if (pending > 0) {
            throw new BizException(400, "你已提交过申请，请等待商家审核");
        }
        Long already = staffMapper.selectCount(Wrappers.<Staff>lambdaQuery()
                .eq(Staff::getCompanyId, companyId).eq(Staff::getUserId, userId));
        if (already > 0) {
            throw new BizException(400, "你已是该公司的员工");
        }
        StaffRequest r = new StaffRequest();
        r.setCompanyId(companyId);
        r.setUserId(userId);
        r.setRole(role);
        r.setStatus(0);
        staffRequestMapper.insert(r);
        Map<String, Object> data = new HashMap<>();
        data.put("id", r.getId());
        data.put("companyName", company.getNickname());
        data.put("role", role);
        return Result.ok(data, "申请已提交，请等待商家审核");
    }

    /** 我的申请（技术人员查看） */
    @GetMapping("/my-applies")
    public Result<List<Map<String, Object>>> myApplies(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Integer userId = currentUserId(authHeader);
        List<StaffRequest> rows = staffRequestMapper.selectList(Wrappers.<StaffRequest>lambdaQuery()
                .eq(StaffRequest::getUserId, userId).orderByDesc(StaffRequest::getId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (StaffRequest r : rows) {
            SysUser company = userMapper.selectById(r.getCompanyId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("companyId", r.getCompanyId());
            m.put("companyName", company == null ? "" : company.getNickname());
            m.put("role", r.getRole());
            m.put("roleLabel", roleLabel(r.getRole(), r.getCompanyId()));
            m.put("status", r.getStatus());
            m.put("createdAt", r.getCreatedAt());
            list.add(m);
        }
        return Result.ok(list);
    }

    /** 商家：收到的申请列表 */
    @GetMapping("/applies")
    public Result<Map<String, Object>> applies(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer pageSize,
            @RequestParam(required = false) Integer status) {
        Integer companyId = currentUserId(authHeader);
        page = PageQuery.normalizePage(page);
        pageSize = PageQuery.normalizePageSize(pageSize);
        Long total = staffRequestMapper.selectCount(Wrappers.<StaffRequest>lambdaQuery()
                .eq(StaffRequest::getCompanyId, companyId)
                .eq(status != null, StaffRequest::getStatus, status));
        List<StaffRequest> rows = staffRequestMapper.selectList(Wrappers.<StaffRequest>lambdaQuery()
                .eq(StaffRequest::getCompanyId, companyId)
                .eq(status != null, StaffRequest::getStatus, status)
                .orderByDesc(StaffRequest::getId)
                .last("limit " + PageQuery.offset(page, pageSize) + "," + pageSize));
        List<Map<String, Object>> list = new ArrayList<>();
        for (StaffRequest r : rows) {
            SysUser u = userMapper.selectById(r.getUserId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("userId", r.getUserId());
            m.put("nickname", u == null ? "" : u.getNickname());
            m.put("phone", u == null ? "" : u.getPhone());
            m.put("role", r.getRole());
            m.put("roleLabel", roleLabel(r.getRole(), companyId));
            m.put("status", r.getStatus());
            m.put("createdAt", r.getCreatedAt());
            list.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        return Result.ok(data);
    }

    /** 商家审核申请：status=1 通过(加入员工) / 2 拒绝 */
    @PutMapping("/apply/{id}/review")
    public Result<Void> review(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Integer id,
            @RequestParam Integer status) {
        Integer companyId = currentUserId(authHeader);
        StaffRequest r = staffRequestMapper.selectOne(Wrappers.<StaffRequest>lambdaQuery()
                .eq(StaffRequest::getId, id).eq(StaffRequest::getCompanyId, companyId));
        if (r == null) {
            throw new BizException(404, "申请不存在");
        }
        if (r.getStatus() != null && r.getStatus() != 0) {
            throw new BizException(400, "该申请已处理");
        }
        if (status != null && status == 1) {
            Staff exist = staffMapper.selectOne(Wrappers.<Staff>lambdaQuery()
                    .eq(Staff::getCompanyId, companyId).eq(Staff::getUserId, r.getUserId()));
            if (exist == null) {
                Staff s = new Staff();
                s.setCompanyId(companyId);
                s.setUserId(r.getUserId());
                s.setRole(r.getRole());
                s.setStatus(1);
                staffMapper.insert(s);
            } else {
                exist.setStatus(1);
                exist.setRole(r.getRole());
                staffMapper.updateById(exist);
            }
            r.setStatus(1);
        } else {
            r.setStatus(2);
        }
        r.setReviewedAt(LocalDateTime.now());
        staffRequestMapper.updateById(r);
        return Result.ok(null, status != null && status == 1 ? "已通过，该技术人员已加入你的团队" : "已拒绝申请");
    }

    private String randomPassword() {
        byte[] b = new byte[12];
        RANDOM.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }
}
