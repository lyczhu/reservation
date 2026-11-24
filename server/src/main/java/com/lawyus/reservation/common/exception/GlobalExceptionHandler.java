package com.lawyus.reservation.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：把业务异常与框架异常统一转换为 Result 响应，避免把堆栈细节暴露给客户端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.fail(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null
                ? ErrorCode.PARAM_INVALID.getMessage()
                : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return Result.fail(ErrorCode.PARAM_INVALID, message);
    }

    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLogin(NotLoginException e) {
        return Result.fail(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public Result<Void> handleUnreadableBody(Exception e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "请求体格式错误");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "缺少参数 " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "参数 " + e.getName() + " 格式错误");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadSize(MaxUploadSizeExceededException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "文件超过 2MB 限制");
    }

    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    public Result<Void> handleForbidden(SaTokenException e) {
        return Result.fail(ErrorCode.FORBIDDEN_ROLE);
    }

    @ExceptionHandler(SaTokenException.class)
    public Result<Void> handleSaToken(SaTokenException e) {
        return Result.fail(ErrorCode.UNAUTHORIZED);
    }

    /** 未匹配到路由或静态资源时按 404 语义返回，不走内部错误。 */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        return Result.fail(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }
}