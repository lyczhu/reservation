package com.lawyus.reservation.common.exception;

import com.lawyus.reservation.common.api.ErrorCode;
import lombok.Getter;

/**
 * 业务异常：携带统一错误码，由 GlobalExceptionHandler 转换为标准响应。
 */
public class BusinessException extends RuntimeException {

    @Getter
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}