package com.flashflow.trade.common;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        var error = exception.errorCode();
        return ResponseEntity.status(error.status())
                .body(ApiResponse.failure(error.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        var error = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(error.status())
                .body(ApiResponse.failure(error.code(), message));
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    ResponseEntity<ApiResponse<Void>> handleMissingRequestValue(ServletRequestBindingException exception) {
        var error = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(error.status())
                .body(ApiResponse.failure(error.code(), exception.getMessage()));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class
    })
    ResponseEntity<ApiResponse<Void>> handleUnreadableRequest(Exception exception) {
        var error = ErrorCode.VALIDATION_FAILED;
        log.debug("Rejected malformed request, traceId={}", TraceContext.currentTraceId(), exception);
        return ResponseEntity.status(error.status())
                .body(ApiResponse.failure(error.code(), error.defaultMessage()));
    }

    @ExceptionHandler({
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            NoResourceFoundException.class
    })
    ResponseEntity<ApiResponse<Void>> handleUnsupportedRequest(Exception exception) {
        HttpStatusCode status = exception instanceof ErrorResponse errorResponse
                ? errorResponse.getStatusCode()
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(ApiResponse.failure("REQUEST_NOT_SUPPORTED", exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        var error = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(error.status())
                .body(ApiResponse.failure(error.code(), error.defaultMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConflict(DataIntegrityViolationException exception) {
        log.warn("Database constraint violation, traceId={}", TraceContext.currentTraceId());
        var error = ErrorCode.REQUEST_PROCESSING;
        return ResponseEntity.status(error.status())
                .body(ApiResponse.failure(error.code(), "请求已被处理，请勿重复提交"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled error, traceId={}", TraceContext.currentTraceId(), exception);
        var error = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(error.status())
                .body(ApiResponse.failure(error.code(), error.defaultMessage()));
    }
}
