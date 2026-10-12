package com.wedding.auth.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wedding.auth.mapper.MerchantRequestMapper;
import com.wedding.auth.mapper.SysUserMapper;
import com.wedding.auth.module.MerchantRequest;
import com.wedding.auth.module.SysUser;
import com.wedding.common.BizException;
import com.wedding.common.JwtUtil;
import com.wedding.common.PageQuery;
import com.wedding.common.Result;
import io.jsonwebtoken.Claims;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家入驻申请：普通用户申请成为婚庆公司(商家)，管理员审核。
 * 审核通过后该账号角色自动转为 wedding_company，并出现在技术人员可申请的商家列表中。
 */
@RestController
@RequestMapping("/api/auth/merchant")
public class MerchantController {

    private final SysUserMapper userMapper;
    private final MerchantRequestMapper merchantRequestMapper;

    public MerchantController(SysUserMapper userMapper, MerchantRequestMapper merchantRequestMapper) {
        this.userMapper = userMapper;
        this.merchantRequestMapper = merchantRequestMapper;
    }

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

    /** 普通用户提交商家入驻申请 */
    @PostMapping("/apply")
    public Result<Map<String, Object>> apply(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> body) {
        Integer userId = currentUserId(authHeader);
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        String shopName = body.get("shopName") == null ? "" : body.get("shopName").trim();
        String contactName = body.get("contactName") == null ? "" : body.get("contactName").trim();
        String phone = body.get("phone") == null ? "" : body.get("phone").trim();
        String address = body.get("address") == null ? "" : body.get("address").trim();
        String businessType = body.get("businessType") == null ? "" : body.get("businessType").trim();
        String licenseNo = body.get("licenseNo") == null ? "" : body.get("licenseNo").trim();
        String licenseImg = body.get("licenseImg") == null ? "" : body.get("licenseImg").trim();
        String introduction = body.get("introduction") == null ? "" : body.get("introduction").trim();
        if (shopName.isEmpty()) {
            throw new BizException(400, "请填写店铺名称");
        }
        if (shopName.length() > 50) {
            throw new BizException(400, "店铺名称不能超过50个字符");
        }
        if (contactName.isEmpty()) {
            throw new BizException(400, "请填写联系人姓名");
        }
        if (contactName.length() > 30) {
            throw new BizException(400, "联系人姓名不能超过30个字符");
        }
        if (phone.length() > 20) {
            throw new BizException(400, "手机号不能超过20个字符");
        }
        if (address.length() > 100) {
            throw new BizException(400, "店铺地址不能超过100个字符");
        }
        if (businessType.length() > 100) {
            throw new BizException(400, "主营服务类型不能超过100个字符");
        }
        if (licenseNo.length() > 50) {
            throw new BizException(400, "营业执照号不能超过50个字符");
        }
        if (licenseImg.length() > 500) {
            throw new BizException(400, "营业执照图片地址不能超过500个字符");
        }
        if (introduction.length() > 500) {
            throw new BizException(400, "店铺简介不能超过500个字符");
        }
        if ("wedding_company".equals(user.getRole()) || "admin".equals(user.getRole())) {
            throw new BizException(400, "你已是商家，无需重复申请");
        }
        Long pending = merchantRequestMapper.selectCount(Wrappers.<MerchantRequest>lambdaQuery()
                .eq(MerchantRequest::getUserId, userId).eq(MerchantRequest::getStatus, 0));
        if (pending != null && pending > 0) {
            throw new BizException(400, "你已提交过申请，请等待管理员审核");
        }
        MerchantRequest r = new MerchantRequest();
        r.setUserId(userId);
        r.setShopName(shopName);
        r.setContactName(contactName);
        r.setPhone(phone);
        r.setAddress(address);
        r.setBusinessType(businessType);
        r.setLicenseNo(licenseNo);
        r.setLicenseImg(licenseImg);
        r.setIntroduction(introduction);
        r.setStatus(0);
        merchantRequestMapper.insert(r);
        Map<String, Object> data = new HashMap<>();
        data.put("id", r.getId());
        data.put("shopName", shopName);
        data.put("status", 0);
        return Result.ok(data, "申请已提交，请等待管理员审核");
    }

    /** 我的商家入驻申请记录 */
    @GetMapping("/my")
    public Result<List<Map<String, Object>>> my(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Integer userId = currentUserId(authHeader);
        List<MerchantRequest> rows = merchantRequestMapper.selectList(Wrappers.<MerchantRequest>lambdaQuery()
                .eq(MerchantRequest::getUserId, userId).orderByDesc(MerchantRequest::getId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (MerchantRequest r : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("shopName", r.getShopName());
            m.put("contactName", r.getContactName());
            m.put("phone", r.getPhone());
            m.put("address", r.getAddress());
            m.put("businessType", r.getBusinessType());
            m.put("licenseNo", r.getLicenseNo());
            m.put("licenseImg", r.getLicenseImg());
            m.put("introduction", r.getIntroduction());
            m.put("status", r.getStatus());
            m.put("createdAt", r.getCreatedAt());
            m.put("reviewedAt", r.getReviewedAt());
            list.add(m);
        }
        return Result.ok(list);
    }

    /** 用户：撤销待审核的入驻申请 */
    @DeleteMapping("/apply/{id}")
    public Result<Void> cancel_apply(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @org.springframework.web.bind.annotation.PathVariable Integer id) {
        Integer userId = currentUserId(authHeader);
        MerchantRequest r = merchantRequestMapper.selectById(id);
        if (r == null || !r.getUserId().equals(userId)) {
            throw new BizException(404, "申请不存在");
        }
        if (r.getStatus() != 0) {
            throw new BizException(400, "仅待审核的申请可撤销");
        }
        merchantRequestMapper.deleteById(id);
        return Result.ok(null);
    }

    /** 管理员：商家入驻申请列表（分页） */
    @GetMapping("/applies")
    public Result<Map<String, Object>> applies(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer pageSize,
            @RequestParam(required = false) Integer status) {
        Integer userId = currentUserId(authHeader);
        SysUser admin = userMapper.selectById(userId);
        if (admin == null || !"admin".equals(admin.getRole())) {
            throw new BizException(403, "仅管理员可查看商家入驻申请");
        }
        page = PageQuery.normalizePage(page);
        pageSize = PageQuery.normalizePageSize(pageSize);
        Long total = merchantRequestMapper.selectCount(Wrappers.<MerchantRequest>lambdaQuery()
                .eq(status != null, MerchantRequest::getStatus, status));
        List<MerchantRequest> rows = merchantRequestMapper.selectList(Wrappers.<MerchantRequest>lambdaQuery()
                .eq(status != null, MerchantRequest::getStatus, status)
                .orderByDesc(MerchantRequest::getId)
                .last("limit " + PageQuery.offset(page, pageSize) + "," + pageSize));
        List<Map<String, Object>> list = new ArrayList<>();
        for (MerchantRequest r : rows) {
            SysUser u = userMapper.selectById(r.getUserId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("userId", r.getUserId());
            m.put("nickname", u == null ? "" : u.getNickname());
            m.put("username", u == null ? "" : u.getUsername());
            m.put("shopName", r.getShopName());
            m.put("contactName", r.getContactName());
            m.put("phone", r.getPhone());
            m.put("address", r.getAddress());
            m.put("businessType", r.getBusinessType());
            m.put("licenseNo", r.getLicenseNo());
            m.put("licenseImg", r.getLicenseImg());
            m.put("introduction", r.getIntroduction());
            m.put("status", r.getStatus());
            m.put("createdAt", r.getCreatedAt());
            m.put("reviewedAt", r.getReviewedAt());
            list.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        return Result.ok(data);
    }

    /** 管理员审核：status=1 通过(角色转为商家) / 2 拒绝 */
    @PutMapping("/apply/{id}/review")
    public Result<Void> review(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Integer id,
            @RequestParam Integer status) {
        Integer userId = currentUserId(authHeader);
        SysUser admin = userMapper.selectById(userId);
        if (admin == null || !"admin".equals(admin.getRole())) {
            throw new BizException(403, "仅管理员可审核商家入驻申请");
        }
        MerchantRequest r = merchantRequestMapper.selectById(id);
        if (r == null) {
            throw new BizException(404, "申请不存在");
        }
        if (r.getStatus() != null && r.getStatus() != 0) {
            throw new BizException(400, "该申请已处理");
        }
        if (status != null && status == 1) {
            SysUser u = userMapper.selectById(r.getUserId());
            if (u == null) {
                throw new BizException(404, "申请人不存在");
            }
            // 账号类型转为商家：角色 wedding_company，同步商家资料
            u.setRole("wedding_company");
            if (StringUtils.hasText(r.getShopName())) {
                u.setShopName(r.getShopName());
            }
            if (StringUtils.hasText(r.getContactName())) {
                u.setContactName(r.getContactName());
            }
            if (StringUtils.hasText(r.getPhone())) {
                u.setPhone(r.getPhone());
            }
            if (StringUtils.hasText(r.getAddress())) {
                u.setAddress(r.getAddress());
            }
            if (StringUtils.hasText(r.getBusinessType())) {
                u.setBusinessType(r.getBusinessType());
            }
            if (StringUtils.hasText(r.getLicenseNo())) {
                u.setLicenseNo(r.getLicenseNo());
            }
            if (StringUtils.hasText(r.getLicenseImg())) {
                u.setLicenseImg(r.getLicenseImg());
            }
            if (StringUtils.hasText(r.getIntroduction())) {
                u.setIntroduction(r.getIntroduction());
            }
            userMapper.updateById(u);
            r.setStatus(1);
        } else {
            r.setStatus(2);
        }
        r.setReviewedAt(LocalDateTime.now());
        merchantRequestMapper.updateById(r);
        return Result.ok(null, status != null && status == 1
                ? "已通过，该账号类型已转为婚庆公司" : "已拒绝申请");
    }

    // ==================== 首页商家展示管理（仅超级管理员） ====================

    private void requireAdmin(String authHeader) {
        Integer userId = currentUserId(authHeader);
        SysUser admin = userMapper.selectById(userId);
        if (admin == null || !"admin".equals(admin.getRole())) {
            throw new BizException(403, "仅超级管理员可管理首页商家");
        }
    }

    /**
     * 管理员：商家列表（用于首页展示管理）
     * 返回所有婚庆公司账号，标记是否在首页展示、排序、是否有在售套餐。
     */
    @GetMapping("/admin/merchants")
    public Result<List<Map<String, Object>>> adminMerchants(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) String keyword) {
        requireAdmin(authHeader);
        var q = Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRole, "wedding_company")
                .orderByAsc(SysUser::getHomeSort)
                .orderByDesc(SysUser::getId);
        if (StringUtils.hasText(keyword)) {
            q.like(SysUser::getShopName, keyword.trim());
        }
        List<SysUser> rows = userMapper.selectList(q);
        List<Map<String, Object>> list = new ArrayList<>();
        for (SysUser u : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("shopName", u.getShopName());
            m.put("nickname", u.getNickname());
            m.put("avatar", u.getAvatar());
            m.put("contactName", u.getContactName());
            m.put("phone", u.getPhone());
            m.put("address", u.getAddress());
            m.put("businessType", u.getBusinessType());
            m.put("status", u.getStatus());
            m.put("homeShow", u.getHomeShow() == null ? 1 : u.getHomeShow());
            m.put("homeSort", u.getHomeSort() == null ? 0 : u.getHomeSort());
            list.add(m);
        }
        return Result.ok(list);
    }

    /** 管理员：设置某商家是否在首页展示 */
    @PutMapping("/admin/merchants/{id}/home-show")
    public Result<Void> setHomeShow(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Integer id,
            @RequestParam Integer homeShow) {
        requireAdmin(authHeader);
        SysUser u = userMapper.selectById(id);
        if (u == null || !"wedding_company".equals(u.getRole())) {
            throw new BizException(404, "商家不存在");
        }
        SysUser upd = new SysUser();
        upd.setId(id);
        upd.setHomeShow(homeShow != null && homeShow == 1 ? 1 : 0);
        userMapper.updateById(upd);
        return Result.ok(null, upd.getHomeShow() == 1 ? "已设为首页展示" : "已取消首页展示");
    }

    /** 管理员：批量保存首页商家顺序（ids 按目标顺序排列，仅对 homeShow=1 的生效） */
    @PutMapping("/admin/merchants/home-sort")
    public Result<Void> saveHomeSort(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, List<Integer>> body) {
        requireAdmin(authHeader);
        List<Integer> ids = body == null ? null : body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return Result.ok(null, "无需要保存的排序");
        }
        int sort = 1;
        for (Integer id : ids) {
            if (id == null) continue;
            SysUser upd = new SysUser();
            upd.setId(id);
            upd.setHomeSort(sort++);
            userMapper.updateById(upd);
        }
        return Result.ok(null, "排序已保存");
    }
}
