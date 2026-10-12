package com.wedding.common;

import lombok.Data;

/**
 * 统一响应结果
 */
@Data
public class Result<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> ok(T data) {
        return ok(data, "success");
    }

    public static <T> Result<T> ok(T data, String msg) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.msg = msg;
        r.data = data;
        return r;
    }

    public static <T> Result<T> fail(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }
}
