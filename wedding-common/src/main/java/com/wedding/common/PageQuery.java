package com.wedding.common;

/**
 * 分页参数校验工具。
 * <p>
 * 防止 pageSize 过大（如 999999）拖垮数据库，统一封顶。
 */
public final class PageQuery {

    private PageQuery() {
    }

    /** 单页最大条数 */
    public static final int MAX_PAGE_SIZE = 100;
    /** 默认单页条数 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 归一化 page：小于 1 时返回 1。
     */
    public static int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    /**
     * 归一化 pageSize：小于 1 返回默认值，超过上限则封顶。
     */
    public static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 计算 SQL offset（配合 limit 使用）。
     */
    public static int offset(Integer page, Integer pageSize) {
        return (normalizePage(page) - 1) * normalizePageSize(pageSize);
    }
}
