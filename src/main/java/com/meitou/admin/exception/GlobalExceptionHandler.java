package com.meitou.admin.exception;

import com.meitou.admin.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import java.sql.SQLException;

/**
 * 全局异常处理器
 * 统一处理异常，返回友好的错误信息
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private HttpStatus resolveBusinessHttpStatus(Integer businessCode) {
        if (businessCode == null) {
            return HttpStatus.BAD_REQUEST;
        }

        if (businessCode >= 400 && businessCode < 600) {
            try {
                return HttpStatus.valueOf(businessCode);
            } catch (Exception ignored) {
                return HttpStatus.BAD_REQUEST;
            }
        }

        if (ErrorCode.INSUFFICIENT_BALANCE.getCode().equals(businessCode)) {
            return HttpStatus.PAYMENT_REQUIRED;
        }

        if (ErrorCode.ACCOUNT_NOT_FOUND.getCode().equals(businessCode) || ErrorCode.USER_NOT_FOUND.getCode().equals(businessCode)) {
            return HttpStatus.NOT_FOUND;
        }

        if (ErrorCode.UNAUTHORIZED.getCode().equals(businessCode)) {
            return HttpStatus.UNAUTHORIZED;
        }

        if (ErrorCode.FORBIDDEN.getCode().equals(businessCode)) {
            return HttpStatus.FORBIDDEN;
        }

        if (businessCode >= 2000 && businessCode < 3000) {
            if (ErrorCode.API_CALL_FAILED.getCode().equals(businessCode) || ErrorCode.API_RESPONSE_ERROR.getCode().equals(businessCode)) {
                // Return 400 instead of 502 to ensure frontend receives the JSON body with error message
                return HttpStatus.BAD_REQUEST;
            }
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        if (businessCode >= 4000 && businessCode < 5000) {
            return HttpStatus.BAD_REQUEST;
        }

        if (businessCode >= 1000 && businessCode < 2000) {
            return HttpStatus.BAD_REQUEST;
        }

        return HttpStatus.BAD_REQUEST;
    }
    
    /**
     * 处理数据库操作异常
     */
    @ExceptionHandler({DataAccessException.class, SQLException.class})
    public ResponseEntity<Result<?>> handleDatabaseException(Exception e) {
        log.error("数据库操作异常: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "数据库操作失败，请联系管理员"));
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(resolveBusinessHttpStatus(e.getCode()))
                .body(Result.error(e.getCode(), e.getMessage()));
    }
    
    /**
     * 处理参数验证异常
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<?>> handleValidationException(Exception e) {
        String message = "参数验证失败";
        
        if (e instanceof MethodArgumentNotValidException) {
            MethodArgumentNotValidException ex = (MethodArgumentNotValidException) e;
            message = ex.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
        } else if (e instanceof BindException) {
            BindException ex = (BindException) e;
            message = ex.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
        }
        
        log.warn("参数验证失败: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, message));
    }
    
    /**
     * 处理运行时异常
     * 区分业务逻辑中未捕获的运行时异常和真正的系统崩溃
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<?>> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: ", e);
        // 运行时异常通常视为系统错误，返回 500
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "系统繁忙，请稍后再试"));
    }
    
    /**
     * 处理其他异常
     * 真正的系统崩溃/未预料的错误 -> HTTP 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e) {
        log.error("系统异常: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, "系统繁忙，请稍后再试"));
    }
}

