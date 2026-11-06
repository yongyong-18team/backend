package com.batong.global.error;

import com.batong.global.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return toResponse(e.getErrorCode());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        return toResponse(ErrorCode.COMMON_VALIDATION);
    }

    // 예외 메시지에 요청 본문(일기 등)이 섞일 수 있어 종류와 스택만 남긴다
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception: {}", e.getClass().getName(), withoutMessage(e));
        return toResponse(ErrorCode.COMMON_INTERNAL_ERROR);
    }

    /** Spring MVC 표준 예외(검증 실패, 405, 415, 404, 업로드 초과 등)를 명세의 에러 코드로 바꾼다. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code;
        if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
            code = ErrorCode.COMMON_NOT_FOUND;
        } else if (statusCode.value() == HttpStatus.PAYLOAD_TOO_LARGE.value()) {
            code = ErrorCode.STT_FILE_TOO_LARGE;
        } else if (statusCode.is5xxServerError()) {
            log.error("MVC exception: {}", ex.getClass().getName(), withoutMessage(ex));
            code = ErrorCode.COMMON_INTERNAL_ERROR;
        } else {
            code = ErrorCode.COMMON_VALIDATION;
        }
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.fail(code));
    }

    private static ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.fail(code));
    }

    private static Throwable withoutMessage(Throwable e) {
        Throwable copy = new Throwable(e.getClass().getName());
        copy.setStackTrace(e.getStackTrace());
        return copy;
    }
}
