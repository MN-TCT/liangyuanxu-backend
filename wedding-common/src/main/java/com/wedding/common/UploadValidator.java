package com.wedding.common;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * 上传文件安全校验工具。
 * <p>
 * 仅校验扩展名是不够的：攻击者可将任意文件改名为 .png 上传。
 * 这里通过读取文件头「魔数」验证真实类型，双重校验（扩展名 + 魔数）。
 */
public final class UploadValidator {

    private UploadValidator() {
    }

    /** 允许的图片扩展名 */
    private static final String[] IMAGE_EXTS = {"jpg", "jpeg", "png", "webp", "gif"};
    /** 允许的视频扩展名 */
    private static final String[] VIDEO_EXTS = {"mp4", "mov", "avi", "mkv", "webm", "m4v"};

    /**
     * 校验图片文件：扩展名 + 魔数 + 大小。
     *
     * @param file      上传文件
     * @param maxBytes  最大字节数
     * @param fieldName 字段名（用于错误提示）
     */
    public static void validateImage(MultipartFile file, long maxBytes, String fieldName) {
        validate(file, IMAGE_EXTS, maxBytes, fieldName);
        byte[] head = readHead(file, 16);
        if (!isImage(head)) {
            throw new BizException(fieldName + "不是有效的图片文件（内容校验未通过）");
        }
    }

    /**
     * 校验视频文件：扩展名 + 魔数 + 大小。
     */
    public static void validateVideo(MultipartFile file, long maxBytes, String fieldName) {
        validate(file, VIDEO_EXTS, maxBytes, fieldName);
        byte[] head = readHead(file, 16);
        if (!isVideo(head)) {
            throw new BizException(fieldName + "不是有效的视频文件（内容校验未通过）");
        }
    }

    private static void validate(MultipartFile file, String[] allowedExts, long maxBytes, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw new BizException(fieldName + "不能为空");
        }
        if (file.getSize() > maxBytes) {
            throw new BizException(fieldName + "大小超过限制（最大 " + (maxBytes / 1024 / 1024) + "MB）");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (ext.isEmpty() || !Arrays.asList(allowedExts).contains(ext)) {
            throw new BizException(fieldName + "格式不支持，仅允许：" + String.join("/", allowedExts));
        }
    }

    /** 提取小写扩展名（不含点）；无扩展名返回空串 */
    public static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] readHead(MultipartFile file, int len) {
        try (InputStream in = file.getInputStream()) {
            byte[] buf = new byte[len];
            int read = in.read(buf);
            if (read <= 0) {
                return new byte[0];
            }
            return read == len ? buf : Arrays.copyOf(buf, read);
        } catch (IOException e) {
            throw new BizException("读取上传文件失败：" + e.getMessage());
        }
    }

    /** 通过魔数判断是否为图片 */
    private static boolean isImage(byte[] h) {
        if (h.length < 4) {
            return false;
        }
        // JPEG: FF D8 FF
        if ((h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47
        if ((h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
            return true;
        }
        // GIF: 47 49 46 38 ("GIF8")
        if (h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8') {
            return true;
        }
        // WEBP: RIFF....WEBP
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return true;
        }
        return false;
    }

    /** 通过魔数判断是否为视频 */
    private static boolean isVideo(byte[] h) {
        if (h.length < 12) {
            return false;
        }
        // MP4/MOV/M4V/AVI 等基于 ISO BMFF：第 4~7 字节为 'ftyp'
        if (h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p') {
            return true;
        }
        // AVI: "RIFF"...."AVI "
        if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'A' && h[9] == 'V' && h[10] == 'I' && h[11] == ' ') {
            return true;
        }
        // MKV/WebM: EBML 头 1A 45 DF A3
        if ((h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45
                && (h[2] & 0xFF) == 0xDF && (h[3] & 0xFF) == 0xA3) {
            return true;
        }
        // MOV 旧格式：ftyp 变体已覆盖；部分 QuickTime 以 'moov'/'mdat' 开头（宽松放行前 8 字节含 ftyp）
        return false;
    }
}
