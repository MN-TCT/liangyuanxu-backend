package com.wedding.booking.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wedding.common.AuthContext;
import com.wedding.common.BizException;
import com.wedding.common.PageQuery;
import com.wedding.common.Result;
import com.wedding.booking.mapper.BookingOrderMapper;
import com.wedding.booking.mapper.BookingStaffAssignmentMapper;
import com.wedding.booking.module.BookingOrder;
import com.wedding.booking.module.BookingStaffAssignment;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/booking")
public class BookingController {

    private final BookingOrderMapper orderMapper;
    private final BookingStaffAssignmentMapper assignmentMapper;
    private final JdbcTemplate jdbcTemplate;

    public BookingController(BookingOrderMapper orderMapper, BookingStaffAssignmentMapper assignmentMapper,
                             JdbcTemplate jdbcTemplate) {
        this.orderMapper = orderMapper;
        this.assignmentMapper = assignmentMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("booking-service ok");
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) String keyword,
                                            HttpServletRequest request) {
        page = PageQuery.normalizePage(page);
        pageSize = PageQuery.normalizePageSize(pageSize);
        Integer userId = AuthContext.userId(request);
        String role = AuthContext.role(request);
        Page<BookingOrder> pg = new Page<>(page, pageSize);
        orderMapper.selectPage(pg, Wrappers.<BookingOrder>lambdaQuery()
                .eq(status != null, BookingOrder::getStatus, status)
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(BookingOrder::getCustomerName, keyword)
                        .or().like(BookingOrder::getPhone, keyword)
                        .or().like(BookingOrder::getPackageName, keyword))
                .eq(userId != null && !"admin".equals(role), BookingOrder::getCompanyId, userId)
                .orderByDesc(BookingOrder::getId));
        Map<String, Object> data = new HashMap<>();
        data.put("list", pg.getRecords());
        data.put("total", pg.getTotal());
        data.put("page", page);
        data.put("pageSize", pageSize);
        return Result.ok(data);
    }

    @GetMapping("/{id}")
    public Result<BookingOrder> detail(@PathVariable Integer id, HttpServletRequest request) {
        BookingOrder o = orderMapper.selectById(id);
        if (o == null) {
            throw new BizException(404, "预约记录不存在");
        }
        checkOwner(o, request);
        return Result.ok(o);
    }

    // 客户提交预约（公开，companyId 归属所选套餐的商家）
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody BookingOrder body) {
        if (!StringUtils.hasText(body.getCustomerName()) || !StringUtils.hasText(body.getPhone())) {
            throw new BizException("姓名和联系方式不能为空");
        }
        if (body.getCompanyId() == null) {
            throw new BizException("缺少归属商家信息");
        }
        orderMapper.insert(body);
        Map<String, Object> data = new HashMap<>();
        data.put("id", body.getId());
        return Result.ok(data, "预约提交成功");
    }

    // 商家手动创建订单（当前登录商家归属；支持套餐/婚纱/场景/价格/职位分配）
    @PostMapping("/merchant-create")
    public Result<Map<String, Object>> merchantCreate(@RequestBody Map<String, Object> body,
                                                      HttpServletRequest request) {
        Integer userId = AuthContext.userId(request);
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        String customerName = str(body.get("customerName"));
        String phone = str(body.get("phone"));
        if (customerName.isEmpty() || phone.isEmpty()) {
            throw new BizException("客户姓名和联系电话不能为空");
        }
        if (customerName.length() > 20) {
            throw new BizException("客户姓名不能超过 20 个字");
        }
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new BizException("手机号格式不正确");
        }
        String packageContent = str(body.get("packageContent"));
        if (packageContent.length() > 500) {
            throw new BizException("套餐内容不能超过 500 个字");
        }
        String shootLocation = str(body.get("shootLocation"));
        if (shootLocation.length() > 100) {
            throw new BizException("拍摄位置不能超过 100 个字");
        }
        String remark = str(body.get("remark"));
        if (remark.length() > 200) {
            throw new BizException("备注不能超过 200 个字");
        }
        Integer dressCount = body.get("dressCount") == null ? 0 : intVal(body.get("dressCount"));
        if (dressCount == null || dressCount < 0 || dressCount > 99) {
            throw new BizException("婚纱套数应在 0 ~ 99 之间");
        }
        java.math.BigDecimal price = null;
        if (body.get("price") != null && !String.valueOf(body.get("price")).isEmpty()) {
            try {
                price = new java.math.BigDecimal(String.valueOf(body.get("price")));
            } catch (Exception e) {
                throw new BizException("价格格式不正确");
            }
            if (price.compareTo(java.math.BigDecimal.ZERO) < 0) {
                throw new BizException("价格不能为负数");
            }
            if (price.compareTo(new java.math.BigDecimal("99999999")) > 0) {
                throw new BizException("价格超出可录入范围");
            }
        }
        String planDate = str(body.get("planDate"));
        java.time.LocalDate planLocalDate = null;
        if (!planDate.isEmpty()) {
            try {
                planLocalDate = java.time.LocalDate.parse(planDate);
            } catch (Exception e) {
                throw new BizException("拍摄日期格式不正确");
            }
            if (planLocalDate.isBefore(java.time.LocalDate.now())) {
                throw new BizException("拍摄日期不能早于今天");
            }
        }
        BookingOrder o = new BookingOrder();
        o.setCustomerName(customerName);
        o.setPhone(phone);
        o.setCompanyId(userId);
        o.setSource(1);
        o.setStatus(body.get("status") == null ? 0 : intVal(body.get("status")));
        o.setPackageId(body.get("packageId") == null ? null : intVal(body.get("packageId")));
        o.setPackageName(str(body.get("packageName")));
        o.setPackageContent(packageContent);
        o.setDressCount(dressCount);
        o.setShootLocation(shootLocation);
        o.setSceneType(str(body.get("sceneType")));
        o.setPrice(price);
        o.setDresses(str(body.get("dresses")));
        o.setRemark(remark);
        o.setPlanDate(planLocalDate);
        orderMapper.insert(o);

        // 职位分配（可选）：body.items = [{role, staffId, staffName}] 或 body.staff = {role, staffId, staffName}
        Object items = body.get("items");
        BookingStaffAssignment first = null;
        if (items instanceof List) {
            for (Object obj : (List<?>) items) {
                if (!(obj instanceof Map)) continue;
                Map<?, ?> item = (Map<?, ?>) obj;
                String role = str(item.get("role"));
                Integer staffId = item.get("staffId") == null ? null : intVal(item.get("staffId"));
                String staffName = str(item.get("staffName"));
                if (role.isEmpty() || staffId == null) continue;
                BookingStaffAssignment a = new BookingStaffAssignment();
                a.setBookingId(o.getId());
                a.setCompanyId(o.getCompanyId());
                a.setRole(role);
                a.setStaffId(staffId);
                a.setStaffName(staffName);
                assignmentMapper.insert(a);
                if (first == null) first = a;
            }
        }
        if (first != null) {
            o.setStaffId(first.getStaffId());
            o.setStaffName(first.getStaffName());
            o.setStaffRole(first.getRole());
            orderMapper.updateById(o);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", o.getId());
        return Result.ok(data, "订单创建成功");
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Integer id, @RequestBody BookingOrder body, HttpServletRequest request) {
        BookingOrder old = orderMapper.selectById(id);
        if (old == null) {
            throw new BizException(404, "预约记录不存在");
        }
        checkOwner(old, request);
        body.setId(id);
        body.setCompanyId(old.getCompanyId());
        orderMapper.updateById(body);
        return Result.ok(null, "更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id, HttpServletRequest request) {
        BookingOrder old = orderMapper.selectById(id);
        if (old == null) {
            throw new BizException(404, "预约记录不存在");
        }
        checkOwner(old, request);
        orderMapper.deleteById(id);
        return Result.ok(null, "删除成功");
    }

    // ==================== 订单职位配置（商家自定义职位 + 人选） ====================

    /** 查询订单职位配置 */
    @GetMapping("/{id}/staff-config")
    public Result<List<Map<String, Object>>> staffConfig(@PathVariable Integer id, HttpServletRequest request) {
        BookingOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException(404, "预约记录不存在");
        }
        checkOwner(order, request);
        List<BookingStaffAssignment> rows = assignmentMapper.selectList(
                Wrappers.<BookingStaffAssignment>lambdaQuery()
                        .eq(BookingStaffAssignment::getBookingId, id)
                        .orderByAsc(BookingStaffAssignment::getId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (BookingStaffAssignment a : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("role", a.getRole());
            m.put("staffId", a.getStaffId());
            m.put("staffName", a.getStaffName());
            list.add(m);
        }
        return Result.ok(list);
    }

    /** 保存订单职位配置（整体替换）：body { items: [ {role, staffId, staffName}, ... ] } */
    @PutMapping("/{id}/staff-config")
    public Result<Void> saveStaffConfig(@PathVariable Integer id, @RequestBody Map<String, Object> body,
                                        HttpServletRequest request) {
        BookingOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException(404, "预约记录不存在");
        }
        checkOwner(order, request);
        assignmentMapper.delete(Wrappers.<BookingStaffAssignment>lambdaQuery()
                .eq(BookingStaffAssignment::getBookingId, id));
        Object items = body.get("items");
        BookingStaffAssignment first = null;
        if (items instanceof List) {
            for (Object obj : (List<?>) items) {
                if (!(obj instanceof Map)) {
                    continue;
                }
                Map<?, ?> item = (Map<?, ?>) obj;
                String role = item.get("role") == null ? "" : String.valueOf(item.get("role"));
                Integer staffId = item.get("staffId") == null ? null
                        : Integer.valueOf(String.valueOf(item.get("staffId")));
                String staffName = item.get("staffName") == null ? "" : String.valueOf(item.get("staffName"));
                if (role.isEmpty() || staffId == null) {
                    continue;
                }
                BookingStaffAssignment a = new BookingStaffAssignment();
                a.setBookingId(id);
                a.setCompanyId(order.getCompanyId());
                a.setRole(role);
                a.setStaffId(staffId);
                a.setStaffName(staffName);
                assignmentMapper.insert(a);
                if (first == null) {
                    first = a;
                }
            }
        }
        // 同步订单冗余主员工字段（列表展示用）
        if (first != null) {
            order.setStaffId(first.getStaffId());
            order.setStaffName(first.getStaffName());
            order.setStaffRole(first.getRole());
        } else {
            order.setStaffId(null);
            order.setStaffName(null);
            order.setStaffRole(null);
        }
        orderMapper.updateById(order);
        return Result.ok(null, "团队配置已保存");
    }

    /** 技术人员：我的任务（我参与的订单），带商家信息与职位费用（按费用可见性过滤）；
     *  可选 companyId：仅返回该商家的任务 */
    @GetMapping("/my-tasks")
    public Result<Object> myTasks(@RequestParam Integer staffId,
                                  @RequestParam(required = false) Integer companyId,
                                  @RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer pageSize) {
        int pg = page == null ? 1 : PageQuery.normalizePage(page);
        int ps = pageSize == null ? 20 : PageQuery.normalizePageSize(pageSize);
        var wrapper = Wrappers.<BookingStaffAssignment>lambdaQuery()
                .eq(BookingStaffAssignment::getStaffId, staffId)
                .eq(companyId != null, BookingStaffAssignment::getCompanyId, companyId)
                .orderByDesc(BookingStaffAssignment::getId);
        List<BookingStaffAssignment> rows;
        long total;
        if (page == null) {
            // 兼容旧调用：返回全量数组
            rows = assignmentMapper.selectList(wrapper);
            total = rows.size();
        } else {
            Page<BookingStaffAssignment> p = assignmentMapper.selectPage(new Page<>(pg, ps), wrapper);
            rows = p.getRecords();
            total = p.getTotal();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (BookingStaffAssignment a : rows) {
            BookingOrder o = orderMapper.selectById(a.getBookingId());
            if (o == null) {
                continue;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("bookingId", a.getBookingId());
            m.put("role", a.getRole());
            m.put("staffId", a.getStaffId());
            m.put("customerName", o.getCustomerName());
            m.put("phone", o.getPhone());
            m.put("packageName", o.getPackageName());
            m.put("planDate", o.getPlanDate());
            m.put("status", o.getStatus());
            m.put("remark", o.getRemark());
            m.put("companyId", o.getCompanyId());
            m.put("companyName", companyName(o.getCompanyId()));
            // 职位费用（按该商家该职位的配置 + 可见性）
            Map<String, Object> fee = roleFee(a.getCompanyId() == null ? o.getCompanyId() : a.getCompanyId(), a.getRole());
            m.put("feeVisible", fee.get("visible"));
            m.put("fee", fee.get("fee"));
            m.put("createdAt", a.getCreatedAt());
            list.add(m);
        }
        if (page == null) {
            return Result.ok(list);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", pg);
        data.put("pageSize", ps);
        data.put("hasMore", (long) pg * ps < total);
        return Result.ok(data);
    }

    /** 商家名称（从 auth 库冗余查询） */
    private String companyName(Integer companyId) {
        if (companyId == null) {
            return "";
        }
        try {
            List<Map<String, Object>> r = jdbcTemplate.queryForList(
                    "SELECT COALESCE(NULLIF(shop_name,''), nickname, username) AS n FROM wedding_auth.sys_user WHERE id=?",
                    companyId);
            return r.isEmpty() ? "" : String.valueOf(r.get(0).get("n"));
        } catch (Exception e) {
            return "";
        }
    }

    /** 职位费用：返回 {fee, visible}；未配置时 visible=false */
    private Map<String, Object> roleFee(Integer companyId, String role) {
        Map<String, Object> out = new HashMap<>();
        out.put("fee", null);
        out.put("visible", false);
        if (companyId == null || role == null || role.isEmpty()) {
            return out;
        }
        try {
            List<Map<String, Object>> r = jdbcTemplate.queryForList(
                    "SELECT fee, fee_visible FROM wedding_auth.staff_role_config WHERE company_id=? AND role_code=? LIMIT 1",
                    companyId, role);
            if (!r.isEmpty()) {
                Map<String, Object> row = r.get(0);
                Object fee = row.get("fee");
                Object vis = row.get("fee_visible");
                boolean visible = vis != null && "1".equals(String.valueOf(vis));
                out.put("fee", fee);
                out.put("visible", visible);
            }
        } catch (Exception ignore) {
            // 忽略
        }
        return out;
    }

    // 统计看板（按当前商家；admin 全量）
    @GetMapping("/stats/overview")
    public Result<Map<String, Object>> stats(HttpServletRequest request) {
        Integer userId = AuthContext.userId(request);
        String role = AuthContext.role(request);
        boolean scoped = userId != null && !"admin".equals(role);
        Map<String, Object> data = new HashMap<>();
        if (scoped) {
            data.put("total", orderMapper.selectCount(Wrappers.<BookingOrder>lambdaQuery().eq(BookingOrder::getCompanyId, userId)));
            data.put("pending", orderMapper.selectCount(Wrappers.<BookingOrder>lambdaQuery().eq(BookingOrder::getCompanyId, userId).eq(BookingOrder::getStatus, 0)));
            data.put("contacted", orderMapper.selectCount(Wrappers.<BookingOrder>lambdaQuery().eq(BookingOrder::getCompanyId, userId).eq(BookingOrder::getStatus, 1)));
            data.put("done", orderMapper.selectCount(Wrappers.<BookingOrder>lambdaQuery().eq(BookingOrder::getCompanyId, userId).eq(BookingOrder::getStatus, 2)));
            data.put("cancel", orderMapper.selectCount(Wrappers.<BookingOrder>lambdaQuery().eq(BookingOrder::getCompanyId, userId).eq(BookingOrder::getStatus, 3)));
        } else {
            data.put("total", orderMapper.selectCount(null));
            data.put("pending", orderMapper.selectCount(Wrappers.<BookingOrder>lambdaQuery().eq(BookingOrder::getStatus, 0)));
            data.put("contacted", orderMapper.selectCount(Wrappers.<BookingOrder>lambdaQuery().eq(BookingOrder::getStatus, 1)));
            data.put("done", orderMapper.selectCount(Wrappers.<BookingOrder>lambdaQuery().eq(BookingOrder::getStatus, 2)));
            data.put("cancel", orderMapper.selectCount(Wrappers.<BookingOrder>lambdaQuery().eq(BookingOrder::getStatus, 3)));
        }
        return Result.ok(data);
    }

    // ============ 当前登录商家 ============

    /** 商家只能操作自己的订单；admin 可操作全部 */
    private void checkOwner(BookingOrder o, HttpServletRequest request) {
        AuthContext.checkOwner(o.getCompanyId(), request);
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static Integer intVal(Object v) {
        try {
            return Integer.valueOf(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
