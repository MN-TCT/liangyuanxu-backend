package com.wedding.auth.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wedding.auth.mapper.SysUserMapper;
import com.wedding.auth.module.SysUser;
import com.wedding.common.BizException;
import com.wedding.common.JwtUtil;
import com.wedding.common.PageQuery;
import com.wedding.common.Result;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 头像图片目录（与包服务共享 covers 卷，更换头像时删除旧文件） */
    @org.springframework.beans.factory.annotation.Value("${wedding.cover-dir:../uploads/covers}")
    private String coverDir;

    /**
     * 短信开发态开关。
     * true  ：/sms/send 会在响应中返回 devCode（验证码明文），仅限本地开发。
     * false ：不返回 devCode（生产默认），需接入真实短信服务商下发。
     * 通过环境变量 SMS_DEV_MODE 控制，默认 false，避免上线误暴露验证码。
     */
    @org.springframework.beans.factory.annotation.Value("${wedding.sms.dev-mode:false}")
    private boolean smsDevMode;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 允许的角色：管理员/商家、婚庆公司、跟妆师、摄影师、普通用户(新人) */
    private static final List<String> VALID_ROLES = Arrays.asList(
            "admin", "wedding_company", "makeup_artist", "photographer", "customer");

    public AuthController(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("auth-service ok");
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return Result.fail(400, "用户名和密码不能为空");
        }
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username).eq(SysUser::getStatus, 1));
        if (user == null || !encoder.matches(password, user.getPasswordHash())) {
            return Result.fail(401, "用户名或密码错误");
        }
        return okWithToken(user);
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String nickname = body.getOrDefault("nickname", "");
        String phone = body.get("phone");
        String role = validRole(body.get("role"));
        if (username == null || password == null) {
            return Result.fail(400, "用户名和密码不能为空");
        }
        if (password.length() < 6) {
            return Result.fail(400, "密码长度不能少于 6 位");
        }
        if (phone == null || !phone.matches("^1\\d{10}$")) {
            return Result.fail(400, "请输入正确的 11 位手机号");
        }
        String smsCode = body.get("smsCode");
        String cachedCode = SMS_CODES.get(phone);
        if (cachedCode == null || !cachedCode.equals(smsCode)) {
            return Result.fail(400, "短信验证码错误或已过期");
        }
        SMS_CODES.remove(phone);
        Long phoneCount = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getPhone, phone));
        if (phoneCount > 0) {
            return Result.fail(400, "该手机号已注册");
        }
        Long count = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (count > 0) {
            return Result.fail(400, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setNickname(nickname);
        user.setShopName(body.getOrDefault("shopName", ""));
        user.setPhone(phone);
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("username", username);
        return Result.ok(data, "注册成功");
    }

    // ================= 短信验证码（开发态模拟，上线接阿里云/腾讯云短信） =================
    private static final Map<String, String> SMS_CODES = new ConcurrentHashMap<>();
    private static final Map<String, Long> SMS_CODE_TIME = new ConcurrentHashMap<>();

    /** 发送短信验证码：开发态直接返回 code，上线后改为调用短信服务商 */
    @PostMapping("/sms/send")
    public Result<Map<String, Object>> sendSms(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        if (phone == null || !phone.matches("^1\\d{10}$")) {
            throw new BizException(400, "请输入正确的 11 位手机号");
        }
        // 6 位随机数字验证码
        String code = String.valueOf(100_000 + RANDOM.nextInt(900_000));
        // 先清理过期验证码
        long now = System.currentTimeMillis();
        SMS_CODES.entrySet().removeIf(e -> now - SMS_CODE_TIME.getOrDefault(e.getKey(), 0L) > 300_000);
        SMS_CODE_TIME.put(phone, now);
        SMS_CODES.put(phone, code);
        Map<String, Object> data = new HashMap<>();
        data.put("phone", phone);
        // 仅开发态返回验证码明文，生产环境不返回（需接入短信服务商下发）
        if (smsDevMode) {
            data.put("devCode", code);
        }
        return Result.ok(data, "验证码已发送");
    }

    /** 短信验证码登录：手机号+验证码，用户不存在则自动注册为普通用户 */
    @PostMapping("/sms/login")
    public Result<Map<String, Object>> smsLogin(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String smsCode = body.get("smsCode");
        if (phone == null || !phone.matches("^1\\d{10}$")) {
            throw new BizException(400, "请输入正确的 11 位手机号");
        }
        String cached = SMS_CODES.get(phone);
        if (cached == null || !cached.equals(smsCode)) {
            throw new BizException(400, "验证码错误或已过期");
        }
        SMS_CODES.remove(phone);
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getPhone, phone));
        if (user == null) {
            throw new BizException(404, "该手机号未注册，请先注册账号");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(403, "账号已被禁用");
        }
        return okWithToken(user);
    }

    // ================= 微信一键登录 =================

    @PostMapping("/wx-login")
    public Result<Map<String, Object>> wxLogin(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isEmpty()) {
            return Result.fail(400, "微信登录 code 不能为空");
        }
        // 1) 用 code 换 openid（配置真实 AppID/Secret 则调微信 code2session，否则开发态降级）
        String openid = fetchOpenId(code);
        // 2) 按 openid 查找已绑定账号
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getOpenid, openid));
        if (user == null) {
            // 3) 首次微信登录 -> 自动注册
            String role = validRole(body.get("role"));
            String nickname = body.getOrDefault("nickname", "");
            String username = "wx_" + openid;
            if (username.length() > 50) {
                username = username.substring(0, 50);
            }
            if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username)) > 0) {
                username = username + "_" + (System.currentTimeMillis() % 100000);
            }
            user = new SysUser();
            user.setUsername(username);
            user.setPasswordHash(encoder.encode(randomPassword()));
            user.setNickname(nickname.isEmpty() ? "微信用户" : nickname);
            user.setShopName(body.getOrDefault("shopName", ""));
            user.setRole(role);
            user.setOpenid(openid);
            user.setStatus(1);
            userMapper.insert(user);
        } else if (user.getStatus() == null || user.getStatus() != 1) {
            return Result.fail(403, "该账号已被禁用");
        }
        return okWithToken(user);
    }

    private String fetchOpenId(String code) {
        String appid = System.getenv("WX_APPID");
        String secret = System.getenv("WX_SECRET");
        if (appid == null || appid.isEmpty() || secret == null || secret.isEmpty()) {
            // 开发态：未配置真实 AppID/Secret，用 dev_ + code 模拟 openid，方便本地调试
            return "dev_" + code;
        }
        try {
            String urlStr = "https://api.weixin.qq.com/sns/jscode2session?appid=" + appid
                    + "&secret=" + secret + "&js_code=" + code + "&grant_type=authorization_code";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            String resp = sb.toString();
            int idx = resp.indexOf("\"openid\":\"");
            if (idx >= 0) {
                int start = idx + "\"openid\":\"".length();
                int end = resp.indexOf("\"", start);
                return resp.substring(start, end);
            }
            throw new BizException(400, "微信登录失败：" + resp);
        } catch (Exception e) {
            if (e instanceof BizException) {
                throw (BizException) e;
            }
            throw new BizException(400, "微信登录失败，请稍后重试");
        }
    }

    private String randomPassword() {
        byte[] b = new byte[12];
        RANDOM.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    // ================= 用户管理（仅 admin） =================

    private void requireAdmin(String authHeader) {
        String token = resolveToken(authHeader);
        Claims claims;
        try {
            claims = token == null ? null : JwtUtil.parseToken(token);
        } catch (Exception e) {
            throw new BizException(401, "登录状态已失效，请重新登录");
        }
        if (claims == null || !"admin".equals(claims.get("role", String.class))) {
            throw new BizException(403, "无权限：仅管理员可操作");
        }
    }

    @GetMapping("/user/list")
    public Result<Map<String, Object>> userList(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword) {
        requireAdmin(authHeader);
        page = PageQuery.normalizePage(page);
        pageSize = PageQuery.normalizePageSize(pageSize);
        boolean hasRole = role != null && !role.isEmpty();
        boolean hasKw = keyword != null && !keyword.isEmpty();
        Long total = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(hasRole, SysUser::getRole, role)
                .and(hasKw, w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getNickname, keyword)));
        List<SysUser> rows = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(hasRole, SysUser::getRole, role)
                .and(hasKw, w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getNickname, keyword))
                .orderByDesc(SysUser::getId)
                .last("limit " + PageQuery.offset(page, pageSize) + "," + pageSize));
        List<Map<String, Object>> list = new ArrayList<>();
        for (SysUser u : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("shopName", u.getShopName());
            m.put("phone", u.getPhone());
            m.put("role", com.wedding.common.RoleUtil.toCode(u.getRole()));
            m.put("status", u.getStatus());
            m.put("createdAt", u.getCreatedAt());
            list.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        return Result.ok(data);
    }

    @PostMapping("/user")
    public Result<Map<String, Object>> createUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> body) {
        requireAdmin(authHeader);
        String username = body.get("username");
        String password = body.get("password");
        String nickname = body.getOrDefault("nickname", "");
        String role = validRole(body.get("role"));
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return Result.fail(400, "用户名和密码不能为空");
        }
        if (password.length() < 6) {
            return Result.fail(400, "密码长度不能少于 6 位");
        }
        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username)) > 0) {
            return Result.fail(400, "用户名已存在");
        }
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(password));
        u.setNickname(nickname);
        u.setRole(role);
        u.setStatus(1);
        userMapper.insert(u);
        return Result.ok(Collections.singletonMap("id", u.getId()), "用户创建成功");
    }

    @PutMapping("/user/{id}/status")
    public Result<Void> updateUserStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Integer id,
            @RequestParam Integer status) {
        requireAdmin(authHeader);
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(404, "用户不存在");
        }
        u.setStatus(status == null ? 1 : status);
        userMapper.updateById(u);
        return Result.ok(null, status != null && status == 0 ? "已禁用" : "已启用");
    }

    // ============ 前端对接：用户信息 / 权限码 ============

    private String resolveToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private Result<Map<String, Object>> okWithToken(SysUser user) {
        String token = JwtUtil.generateToken(user.getId(), user.getUsername(), com.wedding.common.RoleUtil.toCode(user.getRole()));
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        Map<String, Object> u = new HashMap<>();
        u.put("id", user.getId());
        u.put("username", user.getUsername());
        u.put("nickname", user.getNickname());
        u.put("shopName", user.getShopName());
        u.put("contactName", user.getContactName());
        u.put("phone", user.getPhone());
        u.put("address", user.getAddress());
        u.put("businessType", user.getBusinessType());
        u.put("licenseNo", user.getLicenseNo());
        u.put("licenseImg", user.getLicenseImg());
        u.put("avatar", user.getAvatar());
        u.put("introduction", user.getIntroduction());
        // 归一化角色：历史中文名（如「摄影师」）统一返回英文编码，保证前端菜单/权限过滤一致
        u.put("role", com.wedding.common.RoleUtil.toCode(user.getRole()));
        data.put("user", u);
        return Result.ok(data, "登录成功");
    }

    @GetMapping("/user/info")
    public Result<Map<String, Object>> userInfo(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = resolveToken(authHeader);
        Claims claims;
        try {
            claims = token == null ? null : JwtUtil.parseToken(token);
        } catch (Exception e) {
            throw new BizException(401, "登录状态已失效，请重新登录");
        }
        Integer id = claims == null ? null : claims.get("id", Integer.class);
        SysUser user = id == null ? null : userMapper.selectById(id);
        if (user == null) {
            throw new BizException(401, "用户不存在或已禁用");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("userId", String.valueOf(user.getId()));
        data.put("username", user.getUsername());
        data.put("realName", user.getNickname());
        data.put("shopName", user.getShopName());
        data.put("contactName", user.getContactName());
        data.put("phone", user.getPhone());
        data.put("address", user.getAddress());
        data.put("businessType", user.getBusinessType());
        data.put("licenseNo", user.getLicenseNo());
        data.put("licenseImg", user.getLicenseImg());
        data.put("roles", Collections.singletonList(com.wedding.common.RoleUtil.toCode(user.getRole())));
        data.put("avatar", user.getAvatar() == null ? "" : user.getAvatar());
        data.put("desc", user.getIntroduction() == null ? "" : user.getIntroduction());
        boolean isMerchant = user.getShopName() != null && !user.getShopName().isEmpty();
        data.put("isMerchant", isMerchant);
        data.put("homePath", "/wedding/package");
        data.put("token", token);
        return Result.ok(data);
    }

    // ============ 个人中心：更新资料 / 修改密码 ============

    /** 更新当前登录用户资料（昵称/店铺名称/手机号/头像/简介） */
    @PutMapping("/user/profile")
    public Result<Map<String, Object>> updateProfile(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> body) {
        String token = resolveToken(authHeader);
        Claims claims;
        try {
            claims = token == null ? null : JwtUtil.parseToken(token);
        } catch (Exception e) {
            throw new BizException(401, "登录状态已失效，请重新登录");
        }
        Integer id = claims == null ? null : claims.get("id", Integer.class);
        SysUser user = id == null ? null : userMapper.selectById(id);
        if (user == null) {
            throw new BizException(401, "用户不存在或已禁用");
        }
        String nickname = body.getOrDefault("nickname", user.getNickname());
        String shopName = body.getOrDefault("shopName", user.getShopName());
        String phone = body.getOrDefault("phone", user.getPhone());
        String avatar = body.getOrDefault("avatar", user.getAvatar());
        String introduction = body.getOrDefault("introduction", user.getIntroduction());
        if (nickname != null && nickname.length() > 50) {
            throw new BizException(400, "昵称不能超过 50 个字符");
        }
        if (shopName != null && shopName.length() > 50) {
            throw new BizException(400, "店铺名称不能超过 50 个字符");
        }
        if (introduction != null && introduction.length() > 500) {
            throw new BizException(400, "个人简介不能超过 500 个字符");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setNickname(nickname);
        update.setShopName(shopName);
        update.setPhone(phone);
        update.setAvatar(avatar);
        update.setIntroduction(introduction);
        userMapper.updateById(update);
        // 更换头像：删除旧头像物理文件（仅本地上传的 /api/package/static/ 图片）
        if (StringUtils.hasText(user.getAvatar())
                && StringUtils.hasText(avatar)
                && !user.getAvatar().equals(avatar)
                && user.getAvatar().contains("/api/package/static/")) {
            deleteCoverFile(user.getAvatar());
        }
        Map<String, Object> data = new HashMap<>();
        data.put("nickname", nickname);
        data.put("shopName", shopName);
        data.put("phone", phone);
        data.put("avatar", avatar == null ? "" : avatar);
        data.put("introduction", introduction == null ? "" : introduction);
        return Result.ok(data, "保存成功");
    }

    /** 修改当前登录用户密码（校验旧密码） */
    @PostMapping("/user/password")
    public Result<Object> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> body) {
        String token = resolveToken(authHeader);
        Claims claims;
        try {
            claims = token == null ? null : JwtUtil.parseToken(token);
        } catch (Exception e) {
            throw new BizException(401, "登录状态已失效，请重新登录");
        }
        Integer id = claims == null ? null : claims.get("id", Integer.class);
        SysUser user = id == null ? null : userMapper.selectById(id);
        if (user == null) {
            throw new BizException(401, "用户不存在或已禁用");
        }
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || oldPassword.isEmpty()) {
            throw new BizException(400, "请输入旧密码");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException(400, "新密码长度不能少于 6 位");
        }
        if (!encoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BizException(400, "旧密码不正确");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPasswordHash(encoder.encode(newPassword));
        userMapper.updateById(update);
        return Result.ok(null, "密码修改成功");
    }

    /** 上传当前登录用户头像（multipart: file），返回静态访问 URL */
    @PostMapping("/user/avatar")
    public Result<Map<String, Object>> uploadAvatar(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        String token = resolveToken(authHeader);
        Claims claims;
        try {
            claims = token == null ? null : JwtUtil.parseToken(token);
        } catch (Exception e) {
            throw new BizException(401, "登录状态已失效，请重新登录");
        }
        Integer id = claims == null ? null : claims.get("id", Integer.class);
        SysUser user = id == null ? null : userMapper.selectById(id);
        if (user == null) {
            throw new BizException(401, "用户不存在或已禁用");
        }
        // 安全校验：扩展名 + 魔数 + 大小（最大 5MB）
        com.wedding.common.UploadValidator.validateImage(file, 5L * 1024 * 1024, "头像");
        String ext = "." + com.wedding.common.UploadValidator.extensionOf(file.getOriginalFilename());
        String filename = "avatar_" + user.getId() + "_" + System.currentTimeMillis()
                + "_" + (int) (Math.random() * 1000000) + ext;
        java.nio.file.Path dir = java.nio.file.Paths.get(coverDir);
        try {
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.copy(file.getInputStream(), dir.resolve(filename));
        } catch (Exception e) {
            throw new BizException("头像保存失败: " + e.getMessage());
        }
        String url = "/api/package/static/" + filename;
        // 删除旧头像物理文件
        if (StringUtils.hasText(user.getAvatar()) && user.getAvatar().contains("/api/package/static/")) {
            deleteCoverFile(user.getAvatar());
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setAvatar(url);
        userMapper.updateById(update);
        Map<String, Object> data = new HashMap<>();
        data.put("avatar", url);
        return Result.ok(data, "头像上传成功");
    }

    /** 删除头像静态文件（从 cover_url 提取文件名，删除 covers 目录下的物理文件） */
    private void deleteCoverFile(String coverUrl) {
        if (coverUrl == null || coverUrl.isEmpty()) return;
        String filename = coverUrl.substring(coverUrl.lastIndexOf('/') + 1);
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(coverDir).resolve(filename));
        } catch (Exception ignored) {
        }
    }

    @GetMapping("/codes")
    public Result<List<String>> codes() {
        return Result.ok(Arrays.asList("admin", "*"));
    }

    // ==================== 微信扫码登录（Web 端，开发态模拟） ====================

    /** ticket -> 扫码状态 */
    private static final Map<String, QrState> QR_TICKETS = new ConcurrentHashMap<>();

    static class QrState {
        volatile String status; // waiting / confirmed / expired
        volatile String token;
        long createdAt = System.currentTimeMillis();
    }

    /** 生成扫码 ticket，前端拿 ticket 显示二维码并轮询 */
    @PostMapping("/wx-qrcode/generate")
    public Result<Map<String, Object>> qrGenerate() {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        QrState st = new QrState();
        st.status = "waiting";
        QR_TICKETS.put(ticket, st);
        // 清理过期 ticket（5 分钟）
        QR_TICKETS.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue().createdAt > 300_000);
        Map<String, Object> data = new HashMap<>();
        data.put("ticket", ticket);
        data.put("qrcodeContent", "wedding://wx-login?ticket=" + ticket);
        return Result.ok(data);
    }

    /** 前端轮询扫码状态；confirmed 时返回 token */
    @GetMapping("/wx-qrcode/poll")
    public Result<Map<String, Object>> qrPoll(@RequestParam String ticket) {
        QrState st = QR_TICKETS.get(ticket);
        Map<String, Object> data = new HashMap<>();
        if (st == null) {
            data.put("status", "expired");
            return Result.ok(data);
        }
        data.put("status", st.status);
        if ("confirmed".equals(st.status)) {
            data.put("token", st.token);
            QR_TICKETS.remove(ticket);
        }
        return Result.ok(data);
    }

    /** 开发态：模拟手机端扫码确认（传入用户名密码，验证后绑定 ticket） */
    @PostMapping("/wx-qrcode/confirm")
    public Result<Object> qrConfirm(@RequestBody Map<String, String> body) {
        String ticket = body.get("ticket");
        String username = body.get("username");
        String password = body.get("password");
        QrState st = ticket == null ? null : QR_TICKETS.get(ticket);
        if (st == null) {
            throw new BizException(400, "二维码已过期，请刷新");
        }
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username).eq(SysUser::getStatus, 1));
        if (user == null || !encoder.matches(password, user.getPasswordHash())) {
            throw new BizException(401, "用户名或密码错误");
        }
        String token = JwtUtil.generateToken(user.getId(), user.getUsername(), com.wedding.common.RoleUtil.toCode(user.getRole()));
        st.status = "confirmed";
        st.token = token;
        return Result.ok(null, "扫码确认成功");
    }

    private String validRole(String role) {
        if (role == null || role.isEmpty()) {
            return "customer";
        }
        // 归一化：兼容历史中文角色名（如「摄影师」-> photographer）
        String code = com.wedding.common.RoleUtil.toCode(role);
        if (VALID_ROLES.contains(code)) {
            return code;
        }
        throw new BizException(400, "非法的角色类型: " + role);
    }
}
