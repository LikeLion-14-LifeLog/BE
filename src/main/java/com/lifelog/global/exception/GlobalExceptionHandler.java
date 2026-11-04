
package com.lifelog.global.exception;

import com.lifelog.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        ApiResponse<Void> response =
                ApiResponse.failure("서버 내부 오류가 발생했습니다.");

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }


    @ExceptionHandler(
            org.springframework.web.servlet.resource.NoResourceFoundException.class
    )
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            org.springframework.web.servlet.resource.NoResourceFoundException e
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("요청한 리소스를 찾을 수 없습니다."));
    }

}
