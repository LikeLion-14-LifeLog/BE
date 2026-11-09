package com.lifelog.domain.major.exception;

import com.lifelog.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MajorErrorCode implements ErrorCode {
    INVALID_KEYWORD("MAJOR_400_1", "검색어는 앞뒤 공백 제거 후 100자 이하여야 합니다.", HttpStatus.BAD_REQUEST),
    CSV_READ_FAILED("MAJOR_500_1", "전공 데이터 파일을 읽을 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_CSV_FORMAT("MAJOR_500_2", "전공 데이터 파일 형식이 올바르지 않습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    CONFLICTING_CSV_CODE("MAJOR_500_3", "같은 학과코드에 서로 다른 전공 데이터가 있습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INIT_LOCK_UNAVAILABLE("MAJOR_500_4", "전공 초기화 잠금 정보를 찾을 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
