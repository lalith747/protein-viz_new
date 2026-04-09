package com.proteinviz.model;

public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private String errorCode;
    private long timestamp = System.currentTimeMillis();

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true; r.data = data; return r;
    }
    public static <T> ApiResponse<T> success(T data, String msg) {
        ApiResponse<T> r = success(data); r.message = msg; return r;
    }
    public static <T> ApiResponse<T> error(String msg, String code) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false; r.message = msg; r.errorCode = code; return r;
    }
    public static <T> ApiResponse<T> error(String msg) { return error(msg, "ERROR"); }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public String getErrorCode() { return errorCode; }
    public long getTimestamp() { return timestamp; }
}
