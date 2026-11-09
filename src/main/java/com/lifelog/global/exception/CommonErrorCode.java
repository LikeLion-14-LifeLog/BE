package com.lifelog.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {
    INVALID_REQUEST("COMMON_400", "잘못된 요청입니다.", HttpStatus.BAD_REQUEST),
    INVALID_PAGE("COMMON_400_1", "페이지 번호는 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
    INVALID_SIZE("COMMON_400_2", "페이지 크기는 1 이상 100 이하여야 합니다.", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("AUTH_401", "로그인이 필요합니다.", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("AUTH_403", "접근할 수 없는 항목입니다.", HttpStatus.FORBIDDEN),
    NOT_FOUND("COMMON_404", "정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("COMMON_405", "지원하지 않는 요청 방식입니다.", HttpStatus.METHOD_NOT_ALLOWED),
    ALREADY_PROCESSED("COMMON_409", "이미 처리된 요청입니다.", HttpStatus.CONFLICT),
    INTERNAL_SERVER_ERROR("COMMON_500", "서버 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
