package com.wedding.video.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wedding.common.AuthContext;
import com.wedding.common.BizException;
import com.wedding.common.PageQuery;
import com.wedding.common.Result;
import com.wedding.common.UploadValidator;
import com.wedding.video.mapper.WeddingVideoMapper;
import com.wedding.video.module.WeddingVideo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/video")
public class VideoController {

    private final WeddingVideoMapper videoMapper;
    @Value("${wedding.upload-dir}")
    private String uploadDir;

    /** 封面图片目录（与包服务共享 covers 卷，删除旧封面物理文件用） */
    @Value("${wedding.cover-dir:../uploads/covers}")
    private String coverDir;

    public VideoController(WeddingVideoMapper videoMapper) {
        this.videoMapper = videoMapper;
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("video-service ok");
    }

    // 商家上传视频（multipart: file + title + description + cover_url + duration_seconds）
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam String title,
                                              @RequestParam(required = false) String description,
                                              @RequestParam(required = false) String coverUrl,
                                              @RequestParam(required = false, defaultValue = "0") int durationSeconds,
                                              HttpServletRequest request) {
        if (file.isEmpty()) {
            throw new BizException("未接收到视频文件");
        }
        // 安全校验：扩展名 + 魔数 + 大小（最大 200MB）
        UploadValidator.validateVideo(file, 200L * 1024 * 1024, "视频");
        String ext = "." + UploadValidator.extensionOf(file.getOriginalFilename());
        String filename = "video_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000000) + ext;
        Path dir = Paths.get(uploadDir);
        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(filename));
        } catch (IOException e) {
            throw new BizException("视频保存失败: " + e.getMessage());
        }
        String videoUrl = "/api/video/static/" + filename;
        WeddingVideo v = new WeddingVideo();
        v.setTitle(title);
        v.setDescription(description);
        v.setVideoUrl(videoUrl);
        v.setCoverUrl(coverUrl);
        v.setDurationSeconds(durationSeconds);
        v.setStatus(1);
        Integer userId = AuthContext.userId(request);
        v.setCompanyId(userId == null ? 1 : userId);
        v.setCreatedBy(userId);
        videoMapper.insert(v);
        Map<String, Object> data = new HashMap<>();
        data.put("id", v.getId());
        data.put("title", title);
        data.put("video_url", videoUrl);
        return Result.ok(data, "视频上传成功");
    }

    // 视频列表；all=1 商家后台看全部（含下架，按当前商家过滤）；all=0 用户端看所有商家上架
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "12") int pageSize,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(required = false, defaultValue = "0") int all,
                                            HttpServletRequest request) {
        page = PageQuery.normalizePage(page);
        pageSize = PageQuery.normalizePageSize(pageSize);
        Integer userId = AuthContext.userId(request);
        String role = AuthContext.role(request);
        Page<WeddingVideo> pg = new Page<>(page, pageSize);
        videoMapper.selectPage(pg, Wrappers.<WeddingVideo>lambdaQuery()
                .like(keyword != null && !keyword.isEmpty(), WeddingVideo::getTitle, keyword)
                .eq(all == 1 && status != null, WeddingVideo::getStatus, status)
                .eq(all == 0, WeddingVideo::getStatus, 1)
                .eq(all == 1 && userId != null && !"admin".equals(role), WeddingVideo::getCompanyId, userId)
                .orderByDesc(WeddingVideo::getId));
        Map<String, Object> data = new HashMap<>();
        data.put("list", pg.getRecords());
        data.put("total", pg.getTotal());
        data.put("page", page);
        data.put("pageSize", pageSize);
        return Result.ok(data);
    }

    @GetMapping("/{id}")
    public Result<WeddingVideo> detail(@PathVariable Integer id) {
        WeddingVideo v = videoMapper.selectById(id);
        if (v == null) {
            throw new BizException(404, "视频不存在");
        }
        return Result.ok(v);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Integer id, @RequestBody WeddingVideo body, HttpServletRequest request) {
        WeddingVideo old = videoMapper.selectById(id);
        if (old == null) {
            throw new BizException(404, "视频不存在");
        }
        checkOwner(old, request);
        body.setId(id);
        body.setCompanyId(old.getCompanyId());
        videoMapper.updateById(body);
        // 封面更换：删除旧封面物理文件（仅本地上传的封面，外部 http 封面跳过）
        if (StringUtils.hasText(old.getCoverUrl())
                && StringUtils.hasText(body.getCoverUrl())
                && !old.getCoverUrl().equals(body.getCoverUrl())
                && old.getCoverUrl().contains("/api/package/static/")) {
            deleteCoverFile(old.getCoverUrl());
        }
        return Result.ok(null, "视频更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id, HttpServletRequest request) {
        WeddingVideo v = videoMapper.selectById(id);
        if (v == null) {
            throw new BizException(404, "视频不存在");
        }
        checkOwner(v, request);
        if (v.getVideoUrl() != null && v.getVideoUrl().contains("/api/video/static/")) {
            String filename = v.getVideoUrl().substring(v.getVideoUrl().lastIndexOf('/') + 1);
            try {
                Files.deleteIfExists(Paths.get(uploadDir).resolve(filename));
            } catch (IOException ignored) {
            }
        }
        // 一并删除封面物理文件（仅本地上传的封面）
        if (v.getCoverUrl() != null && v.getCoverUrl().contains("/api/package/static/")) {
            deleteCoverFile(v.getCoverUrl());
        }
        videoMapper.deleteById(id);
        return Result.ok(null, "视频删除成功");
    }

    /** 删除封面静态文件（从 cover_url 提取文件名，删除包服务 covers 目录下的物理文件） */
    private void deleteCoverFile(String coverUrl) {
        if (coverUrl == null || coverUrl.isEmpty()) return;
        String filename = coverUrl.substring(coverUrl.lastIndexOf('/') + 1);
        try {
            Files.deleteIfExists(Paths.get(coverDir).resolve(filename));
        } catch (IOException ignored) {
        }
    }

    // ============ 当前登录商家 ============

    /** 商家只能操作自己的视频；admin 可操作全部 */
    private void checkOwner(WeddingVideo v, HttpServletRequest request) {
        AuthContext.checkOwner(v.getCompanyId(), request);
    }
}
