package com.wedding.packages.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wedding.common.AuthContext;
import com.wedding.common.BizException;
import com.wedding.common.PageQuery;
import com.wedding.common.Result;
import com.wedding.common.UploadValidator;
import com.wedding.packages.mapper.PackageCategoryMapper;
import com.wedding.packages.mapper.PackageMapper;
import com.wedding.packages.module.PackageCategory;
import com.wedding.packages.module.WeddingPackage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/package")
public class PackageController {

    private final PackageMapper packageMapper;
    private final PackageCategoryMapper categoryMapper;
    private final JdbcTemplate jdbcTemplate;

    @Value("${wedding.cover-dir}")
    private String coverDir;

    public PackageController(PackageMapper packageMapper, PackageCategoryMapper categoryMapper, JdbcTemplate jdbcTemplate) {
        this.packageMapper = packageMapper;
        this.categoryMapper = categoryMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    // 商家上传套餐封面图（multipart: file），返回静态访问 URL
    @PostMapping("/upload/cover")
    public Result<Map<String, Object>> uploadCover(@RequestParam("file") MultipartFile file) {
        // 安全校验：扩展名 + 魔数 + 大小（最大 5MB）
        UploadValidator.validateImage(file, 5L * 1024 * 1024, "封面图");
        String url = saveCoverFile(file);
        Map<String, Object> data = new HashMap<>();
        data.put("cover_url", url);
        return Result.ok(data, "封面上传成功");
    }

    // 商家批量上传套餐封面图（multipart: files），返回 URL 数组
    @PostMapping("/upload/covers")
    public Result<Map<String, Object>> uploadCovers(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new BizException("请选择要上传的图片");
        }
        if (files.length > 9) {
            throw new BizException("一次最多上传 9 张图片");
        }
        List<String> urls = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) continue;
            UploadValidator.validateImage(f, 5L * 1024 * 1024, "封面图");
            urls.add(saveCoverFile(f));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("cover_urls", urls);
        return Result.ok(data, "上传成功");
    }

    /** 保存单个封面文件，返回静态访问 URL */
    private String saveCoverFile(MultipartFile file) {
        String ext = "." + UploadValidator.extensionOf(file.getOriginalFilename());
        String filename = "cover_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000000) + ext;
        Path dir = Paths.get(coverDir);
        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(filename));
        } catch (IOException e) {
            throw new BizException("封面图保存失败: " + e.getMessage());
        }
        return "/api/package/static/" + filename;
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("package-service ok");
    }

    // ============ 分类 ============
    @GetMapping("/categories")
    public Result<List<PackageCategory>> categories() {
        List<PackageCategory> list = categoryMapper.selectList(
                Wrappers.<PackageCategory>lambdaQuery().orderByAsc(PackageCategory::getSort).orderByAsc(PackageCategory::getId));
        return Result.ok(list);
    }

    @PostMapping("/categories")
    public Result<Map<String, Object>> createCategory(@RequestBody PackageCategory body) {
        if (!StringUtils.hasText(body.getName())) {
            throw new BizException("分类名称不能为空");
        }
        checkLen(body.getName(), 16, "分类名称");
        categoryMapper.insert(body);
        Map<String, Object> data = new HashMap<>();
        data.put("id", body.getId());
        data.put("name", body.getName());
        return Result.ok(data, "分类创建成功");
    }

    @PutMapping("/categories/{id}")
    public Result<Void> updateCategory(@PathVariable Integer id, @RequestBody PackageCategory body) {
        if (!StringUtils.hasText(body.getName())) {
            throw new BizException("分类名称不能为空");
        }
        checkLen(body.getName(), 16, "分类名称");
        PackageCategory c = new PackageCategory();
        c.setId(id);
        c.setName(body.getName());
        c.setSort(body.getSort());
        categoryMapper.updateById(c);
        return Result.ok(null, "分类更新成功");
    }

    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Integer id) {
        Long n = packageMapper.selectCount(Wrappers.<WeddingPackage>lambdaQuery().eq(WeddingPackage::getCategoryId, id));
        if (n > 0) {
            throw new BizException("该分类下仍有套餐，无法删除");
        }
        categoryMapper.deleteById(id);
        return Result.ok(null, "分类删除成功");
    }

    // ============ 套餐 ============

    // 用户端首页：所有有上架套餐的商家列表（可按店名搜索；支持分页）
    // 不传 page 时返回全量数组（兼容旧调用）；传 page 时返回 { list, total, page, pageSize, hasMore }
    @GetMapping("/companies")
    public Result<Object> companies(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer pageSize,
                                    @RequestParam(required = false) Boolean homeOnly) {
        List<WeddingPackage> pkgs = packageMapper.selectList(
                Wrappers.<WeddingPackage>lambdaQuery()
                        .select(WeddingPackage::getCompanyId)
                        .eq(WeddingPackage::getStatus, 1)
                        .isNotNull(WeddingPackage::getCompanyId));
        List<Integer> companyIds = pkgs.stream()
                .map(WeddingPackage::getCompanyId).distinct().collect(Collectors.toList());
        if (companyIds.isEmpty()) {
            return Result.ok(page == null ? Collections.emptyList() : emptyPage(page, pageSize));
        }
        StringBuilder ph = new StringBuilder();
        for (int i = 0; i < companyIds.size(); i++) {
            if (i > 0) ph.append(',');
            ph.append('?');
        }
        String sql = "SELECT id, shop_name, avatar FROM wedding_auth.sys_user WHERE id IN (" + ph + ")";
        List<Object> args = new ArrayList<>(companyIds);
        if (StringUtils.hasText(keyword)) {
            sql += " AND shop_name LIKE CONCAT('%', ?, '%')";
            args.add(keyword.trim());
        }
        // 仅首页展示：只看管理员勾选 home_show=1 的商家
        if (Boolean.TRUE.equals(homeOnly)) {
            sql += " AND home_show = 1";
        }
        // 首页展示时按管理员设置顺序（home_sort 越小越前），否则按 id 倒序保证分页稳定
        if (Boolean.TRUE.equals(homeOnly)) {
            sql += " ORDER BY home_sort ASC, id DESC";
        } else {
            sql += " ORDER BY id DESC";
        }
        List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, args.toArray());

        // 统计每个商家的在售套餐数与主营分类（用于商家列表页展示）
        Map<Integer, Integer> countMap = new HashMap<>();
        Map<Integer, Set<String>> catMap = new HashMap<>();
        for (WeddingPackage p : pkgs) {
            Integer cid = p.getCompanyId();
            if (cid == null) continue;
            countMap.merge(cid, 1, Integer::sum);
        }
        // 分类名映射
        Map<Integer, String> categoryNames = new HashMap<>();
        for (PackageCategory c : categoryMapper.selectList(null)) {
            categoryNames.put(c.getId(), c.getName());
        }
        List<WeddingPackage> fullPkgs = packageMapper.selectList(
                Wrappers.<WeddingPackage>lambdaQuery()
                        .eq(WeddingPackage::getStatus, 1)
                        .isNotNull(WeddingPackage::getCompanyId));
        for (WeddingPackage p : fullPkgs) {
            Integer cid = p.getCompanyId();
            if (cid == null || p.getCategoryId() == null) continue;
            String cname = categoryNames.get(p.getCategoryId());
            if (cname == null || cname.isEmpty()) continue;
            catMap.computeIfAbsent(cid, k -> new LinkedHashSet<>()).add(cname);
        }

        // 分页（在内存中对已筛选商家切片）
        int total = users.size();
        int pg = page == null ? 1 : PageQuery.normalizePage(page);
        int ps = pageSize == null ? 20 : PageQuery.normalizePageSize(pageSize);
        List<Map<String, Object>> pageUsers = users;
        if (page != null) {
            int from = Math.min((pg - 1) * ps, total);
            int to = Math.min(from + ps, total);
            pageUsers = users.subList(from, to);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> u : pageUsers) {
            Integer cid = ((Number) u.get("id")).intValue();
            Map<String, Object> m = new HashMap<>();
            m.put("id", cid);
            m.put("shopName", u.get("shop_name") == null ? "" : String.valueOf(u.get("shop_name")));
            m.put("avatar", u.get("avatar") == null ? "" : String.valueOf(u.get("avatar")));
            m.put("packageCount", countMap.getOrDefault(cid, 0));
            Set<String> cats = catMap.getOrDefault(cid, Collections.emptySet());
            m.put("categories", new ArrayList<>(cats));
            result.add(m);
        }
        if (page == null) {
            return Result.ok(result);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", result);
        data.put("total", total);
        data.put("page", pg);
        data.put("pageSize", ps);
        data.put("hasMore", (long) pg * ps < total);
        return Result.ok(data);
    }

    /** 空分页结果 */
    private Map<String, Object> emptyPage(int page, Integer pageSize) {
        Map<String, Object> data = new HashMap<>();
        data.put("list", Collections.emptyList());
        data.put("total", 0);
        data.put("page", page);
        data.put("pageSize", pageSize == null ? 20 : pageSize);
        data.put("hasMore", false);
        return data;
    }

    // scope=public 为用户端公开浏览（所有商家上架套餐）；默认管理端按当前商家过滤（admin 全量）
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer categoryId,
                                            @RequestParam(required = false) Integer companyId,
                                            @RequestParam(required = false) List<Integer> companyIds,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) String scope,
                                            HttpServletRequest request) {
        boolean publicScope = "public".equals(scope);
        page = PageQuery.normalizePage(page);
        pageSize = PageQuery.normalizePageSize(pageSize);
        Integer userId = AuthContext.userId(request);
        String role = AuthContext.role(request);
        Page<WeddingPackage> pg = new Page<>(page, pageSize);
        packageMapper.selectPage(pg, Wrappers.<WeddingPackage>lambdaQuery()
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(WeddingPackage::getName, keyword).or().like(WeddingPackage::getLocation, keyword))
                .eq(categoryId != null, WeddingPackage::getCategoryId, categoryId)
                .eq(companyId != null, WeddingPackage::getCompanyId, companyId)
                .in(companyIds != null && !companyIds.isEmpty(), WeddingPackage::getCompanyId, companyIds)
                .eq(publicScope, WeddingPackage::getStatus, 1)
                .eq(!publicScope && status != null, WeddingPackage::getStatus, status)
                .eq(!publicScope && userId != null && !"admin".equals(role), WeddingPackage::getCompanyId, userId)
                // 用户端：置顶首页的卡片优先，其次按置顶排序，最后按 id 倒序
                .orderByDesc(publicScope, WeddingPackage::getHomePinned)
                .orderByAsc(publicScope, WeddingPackage::getHomeSort)
                .orderByDesc(WeddingPackage::getId));
        List<PackageCategory> cats = categoryMapper.selectList(null);
        Map<Integer, String> catMap = cats.stream().collect(Collectors.toMap(PackageCategory::getId, PackageCategory::getName));
        List<WeddingPackage> rows = pg.getRecords();
        for (WeddingPackage p : rows) {
            p.setCategoryName(catMap.getOrDefault(p.getCategoryId(), ""));
        }
        fillCompanyInfo(rows.stream().map(WeddingPackage::getCompanyId).filter(Objects::nonNull).collect(Collectors.toList()), rows);
        Map<String, Object> data = new HashMap<>();
        data.put("list", rows);
        data.put("total", pg.getTotal());
        data.put("page", page);
        data.put("pageSize", pageSize);
        return Result.ok(data);
    }

    @GetMapping("/{id}")
    public Result<WeddingPackage> detail(@PathVariable Integer id) {
        WeddingPackage p = packageMapper.selectById(id);
        if (p == null) {
            throw new BizException(404, "套餐不存在");
        }
        PackageCategory c = categoryMapper.selectById(p.getCategoryId());
        p.setCategoryName(c == null ? "" : c.getName());
        if (p.getCompanyId() != null) {
            fillCompanyInfo(Collections.singletonList(p.getCompanyId()), Collections.singletonList(p));
        }
        return Result.ok(p);
    }

    /** 跨库查询商家资料（shop_name/avatar/address）填充到套餐记录 */
    private void fillCompanyInfo(List<Integer> companyIds, List<WeddingPackage> rows) {
        if (companyIds.isEmpty()) return;
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < companyIds.size(); i++) {
            if (i > 0) placeholders.append(',');
            placeholders.append('?');
        }
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT id, shop_name, avatar, address FROM wedding_auth.sys_user WHERE id IN (" + placeholders + ")",
                companyIds.toArray());
        Map<Integer, Map<String, Object>> userMap = new HashMap<>();
        for (Map<String, Object> u : users) {
            userMap.put(((Number) u.get("id")).intValue(), u);
        }
        for (WeddingPackage p : rows) {
            Map<String, Object> u = userMap.get(p.getCompanyId());
            if (u == null) continue;
            p.setCompanyName(u.get("shop_name") == null ? "" : String.valueOf(u.get("shop_name")));
            p.setCompanyAvatar(u.get("avatar") == null ? "" : String.valueOf(u.get("avatar")));
            p.setCompanyAddress(u.get("address") == null ? "" : String.valueOf(u.get("address")));
        }
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody WeddingPackage body, HttpServletRequest request) {
        if (!StringUtils.hasText(body.getName()) || body.getCategoryId() == null) {
            throw new BizException("套餐名称和分类不能为空");
        }
        validatePackage(body);
        syncPrimaryCover(body);
        Integer userId = AuthContext.userId(request);
        body.setCompanyId(userId == null ? 1 : userId);
        packageMapper.insert(body);
        Map<String, Object> data = new HashMap<>();
        data.put("id", body.getId());
        return Result.ok(data, "套餐创建成功");
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Integer id, @RequestBody WeddingPackage body, HttpServletRequest request) {
        WeddingPackage old = packageMapper.selectById(id);
        if (old == null) {
            throw new BizException(404, "套餐不存在");
        }
        checkOwner(old, request);
        validatePackage(body);
        syncPrimaryCover(body);
        body.setId(id);
        body.setCompanyId(old.getCompanyId());
        packageMapper.updateById(body);
        // 封面更换：删除被移除的旧封面物理文件，避免堆积
        Set<String> oldCovers = collectCovers(old.getCoverUrls(), old.getCoverUrl());
        Set<String> newCovers = collectCovers(body.getCoverUrls(), body.getCoverUrl());
        for (String c : oldCovers) {
            if (!newCovers.contains(c)) {
                deleteCoverFile(c);
            }
        }
        return Result.ok(null, "套餐更新成功");
    }

    /** 将多张封面的第一张同步为 coverUrl（列表缩略图用）；若 coverUrls 为空则保留原 coverUrl */
    private void syncPrimaryCover(WeddingPackage body) {
        if (!StringUtils.hasText(body.getCoverUrls())) return;
        try {
            List<?> arr = objectMapper.readValue(body.getCoverUrls(), List.class);
            if (!arr.isEmpty() && arr.get(0) instanceof String) {
                body.setCoverUrl((String) arr.get(0));
            } else {
                body.setCoverUrl(null);
            }
        } catch (Exception ignored) {
        }
    }

    /** 汇总封面集合（多张 + 单张），用于比对删除 */
    private Set<String> collectCovers(String coverUrlsJson, String coverUrl) {
        Set<String> set = new HashSet<>();
        if (StringUtils.hasText(coverUrlsJson)) {
            try {
                List<?> arr = objectMapper.readValue(coverUrlsJson, List.class);
                for (Object o : arr) {
                    if (o instanceof String && StringUtils.hasText((String) o)) set.add((String) o);
                }
            } catch (Exception ignored) {
            }
        }
        if (StringUtils.hasText(coverUrl)) set.add(coverUrl);
        return set;
    }

    /** 删除封面静态文件（从 cover_url 提取文件名） */
    private void deleteCoverFile(String coverUrl) {
        if (!StringUtils.hasText(coverUrl)) return;
        String filename = coverUrl.substring(coverUrl.lastIndexOf('/') + 1);
        try {
            Files.deleteIfExists(Paths.get(coverDir).resolve(filename));
        } catch (IOException ignored) {
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id, HttpServletRequest request) {
        WeddingPackage old = packageMapper.selectById(id);
        if (old == null) {
            throw new BizException(404, "套餐不存在");
        }
        checkOwner(old, request);
        packageMapper.deleteById(id);
        // 删除套餐：一并删除封面物理文件，避免孤儿文件堆积
        if (StringUtils.hasText(old.getCoverUrl())) {
            deleteCoverFile(old.getCoverUrl());
        }
        return Result.ok(null, "套餐删除成功");
    }

    // ============ 首页推荐位（仅超级管理员） ============

    /** 用户端首页轮播/置顶卡片：所有已上架且被置顶的卡片，按置顶排序 */
    @GetMapping("/home/pinned")
    public Result<List<WeddingPackage>> homePinned() {
        List<WeddingPackage> rows = packageMapper.selectList(
                Wrappers.<WeddingPackage>lambdaQuery()
                        .eq(WeddingPackage::getStatus, 1)
                        .eq(WeddingPackage::getHomePinned, 1)
                        .orderByAsc(WeddingPackage::getHomeSort)
                        .orderByDesc(WeddingPackage::getId));
        List<PackageCategory> cats = categoryMapper.selectList(null);
        Map<Integer, String> catMap = cats.stream()
                .collect(Collectors.toMap(PackageCategory::getId, PackageCategory::getName));
        for (WeddingPackage p : rows) {
            p.setCategoryName(catMap.getOrDefault(p.getCategoryId(), ""));
        }
        fillCompanyInfo(rows.stream().map(WeddingPackage::getCompanyId)
                .filter(Objects::nonNull).collect(Collectors.toList()), rows);
        return Result.ok(rows);
    }

    /** 管理端：置顶/取消置顶某套餐（仅超级管理员） */
    @PutMapping("/{id}/pin")
    public Result<Void> pin(@PathVariable Integer id,
                            @RequestParam(defaultValue = "1") Integer pinned,
                            @RequestParam(required = false) Integer sort,
                            HttpServletRequest request) {
        AuthContext.requireAdmin(request);
        WeddingPackage old = packageMapper.selectById(id);
        if (old == null) {
            throw new BizException(404, "套餐不存在");
        }
        if (pinned != null && pinned == 1 && old.getStatus() != null && old.getStatus() != 1) {
            throw new BizException("套餐未上架，无法置顶首页");
        }
        WeddingPackage upd = new WeddingPackage();
        upd.setId(id);
        upd.setHomePinned(pinned == null ? 1 : (pinned == 1 ? 1 : 0));
        if (sort != null) {
            upd.setHomeSort(sort);
        } else if (upd.getHomePinned() == 1 && (old.getHomeSort() == null || old.getHomeSort() == 0)) {
            // 未指定排序时，置于末尾
            Long cnt = packageMapper.selectCount(Wrappers.<WeddingPackage>lambdaQuery()
                    .eq(WeddingPackage::getHomePinned, 1));
            upd.setHomeSort((int) (cnt + 1));
        }
        packageMapper.updateById(upd);
        return Result.ok(null, upd.getHomePinned() == 1 ? "已置顶首页" : "已取消置顶");
    }

    /** 管理端：批量调整置顶卡片顺序（传入按目标顺序排列的 id 列表，仅超级管理员） */
    @PutMapping("/home/sort")
    public Result<Void> homeSort(@RequestBody Map<String, List<Integer>> body, HttpServletRequest request) {
        AuthContext.requireAdmin(request);
        List<Integer> ids = body == null ? null : body.get("ids");
        if (ids == null || ids.isEmpty()) {
            throw new BizException("排序列表不能为空");
        }
        int sort = 1;
        for (Integer id : ids) {
            if (id == null) continue;
            WeddingPackage upd = new WeddingPackage();
            upd.setId(id);
            upd.setHomePinned(1);
            upd.setHomeSort(sort++);
            packageMapper.updateById(upd);
        }
        return Result.ok(null, "排序已更新");
    }

    // ============ 当前登录商家 ============

    /** 商家只能操作自己的套餐；admin 可操作全部 */
    private void checkOwner(WeddingPackage p, HttpServletRequest request) {
        AuthContext.checkOwner(p.getCompanyId(), request);
    }

    // ============ 字段长度/范围校验 ============
    private final ObjectMapper objectMapper = new ObjectMapper();

    private void checkLen(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new BizException(field + "不能超过 " + max + " 个字符");
        }
    }

    private void validatePackage(WeddingPackage body) {
        checkLen(body.getName(), 30, "套餐名称");
        checkLen(body.getCoverUrl(), 500, "封面地址");
        checkLen(body.getVideoUrl(), 500, "关联视频地址");
        checkLen(body.getLocation(), 50, "拍摄地点");
        checkLen(body.getDescription(), 5000, "套餐描述");
        // 多张封面：校验 JSON 数组，最多 9 张，单项 ≤500 字符
        if (StringUtils.hasText(body.getCoverUrls())) {
            if (body.getCoverUrls().length() > 5000) {
                throw new BizException("封面图数据过长");
            }
            try {
                List<?> arr = objectMapper.readValue(body.getCoverUrls(), List.class);
                if (arr.size() > 9) {
                    throw new BizException("封面图最多 9 张");
                }
                for (Object item : arr) {
                    if (!(item instanceof String) || ((String) item).length() > 500) {
                        throw new BizException("封面图地址不合法");
                    }
                }
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                throw new BizException("封面图格式不正确");
            }
        }
        if (StringUtils.hasText(body.getIncludesJson())) {
            if (body.getIncludesJson().length() > 5000) {
                throw new BizException("包含服务内容不能超过 5000 个字符");
            }
            try {
                List<?> arr = objectMapper.readValue(body.getIncludesJson(), List.class);
                if (arr.size() > 50) {
                    throw new BizException("包含服务数量不能超过 50 项");
                }
                for (Object item : arr) {
                    if (item instanceof String && ((String) item).length() > 50) {
                        throw new BizException("单个包含服务不能超过 50 个字符");
                    }
                }
            } catch (Exception e) {
                throw new BizException("包含服务格式不正确");
            }
        }
        if (body.getPrice() != null) {
            checkAmount(body.getPrice(), "价格");
        }
        if (body.getOriginalPrice() != null) {
            checkAmount(body.getOriginalPrice(), "原价");
        }
        if (body.getDurationDays() != null
                && (body.getDurationDays() < 1 || body.getDurationDays() > 365)) {
            throw new BizException("拍摄天数需在 1~365 天之间");
        }
    }

    private void checkAmount(BigDecimal amount, String field) {
        if (amount.signum() < 0 || amount.compareTo(new BigDecimal("999999.00")) > 0) {
            throw new BizException(field + "需在 0 ~ 999999 之间");
        }
    }
}