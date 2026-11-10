package com.lifelog.domain.certification.exception;
import com.lifelog.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
@Getter
@RequiredArgsConstructor
public enum CertificationErrorCode implements ErrorCode {
    CSV_READ_FAILED("INTERNAL_SERVER_ERROR", "자격증 파일을 읽을 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_CSV_FORMAT("INTERNAL_SERVER_ERROR", "자격증 파일 형식이 올바르지 않습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INIT_FAILED("INTERNAL_SERVER_ERROR", "자격증 저장에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    IDENTITY_CONFLICT("CONFLICT", "자격증 식별 정보가 충돌합니다.", HttpStatus.CONFLICT);
    private final String code;
    private final String message;
    private final HttpStatus status;
}
