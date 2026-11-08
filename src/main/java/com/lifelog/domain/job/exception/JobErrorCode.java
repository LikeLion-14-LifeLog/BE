package com.lifelog.domain.job.exception;

import com.lifelog.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum JobErrorCode implements ErrorCode {
    INVALID_KEYWORD("JOB_400_1", "검색어는 50자 이하로 입력해주세요.", HttpStatus.BAD_REQUEST),
    INVALID_PAGE("JOB_400_2", "page는 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
    INVALID_SIZE("JOB_400_3", "size는 1~100 사이여야 합니다.", HttpStatus.BAD_REQUEST),
    CSV_READ_FAILED("JOB_500_1", "직무 데이터 파일을 읽을 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_CSV_FORMAT("JOB_500_2", "직무 데이터 파일 형식이 올바르지 않습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
