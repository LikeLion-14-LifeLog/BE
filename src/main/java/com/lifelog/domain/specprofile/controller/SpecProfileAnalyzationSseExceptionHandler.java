package com.lifelog.domain.specprofile.controller;

import com.lifelog.global.exception.CommonErrorCode;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.io.IOException;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = SpecProfileController.class)
public class SpecProfileAnalyzationSseExceptionHandler {
    /** 스트림 시작 전 도메인 오류는 SSE Accept와 무관하게 공통 JSON 오류로 반환한다. @param exception 도메인 오류 @return 오류 응답 */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Object>> handleCustom(CustomException exception) {
        return ResponseEntity.status(exception.getErrorCode().getStatus()).contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(exception.getErrorCode(), exception.getData()));
    }

    /** 경로 변수 형식 오류를 기존 공통 코드로 반환한다. @param exception 형식 오류 @return 오류 응답 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(CommonErrorCode.INVALID_REQUEST));
    }

    /** 이미 시작된 스트림의 연결 오류에서 JSON 본문을 다시 쓰지 않는다. @param exception 연결 오류 @param response 현재 응답 @return 미시작 응답이면 재접속 가능한 503, 커밋된 응답이면 null */
    @ExceptionHandler({AsyncRequestNotUsableException.class, AsyncRequestTimeoutException.class, IOException.class})
    public ResponseEntity<Void> handleDisconnected(Exception exception, HttpServletResponse response) {
        log.debug("[SpecProfileAnalyzationSseExceptionHandler] SSE 연결 종료 | type: {}", exception.getClass().getSimpleName());
        return response.isCommitted() ? null : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}
