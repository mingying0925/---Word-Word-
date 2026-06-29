package com.skillbridge.service;

/**
 * 自定义业务异常，用于在 Service 层抛出可预期的业务错误。
 */
public class BusinessException extends RuntimeException {

    private final int statusCode;

    public BusinessException(String message) {
        super(message);
        this.statusCode = 400;
    }

    /** @deprecated 未使用，保留仅为向后兼容 */
    @Deprecated
    public BusinessException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 400;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
