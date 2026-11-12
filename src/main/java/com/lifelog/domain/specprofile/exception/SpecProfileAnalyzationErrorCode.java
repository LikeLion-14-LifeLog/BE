package com.lifelog.domain.specprofile.exception;

import com.lifelog.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SpecProfileAnalyzationErrorCode implements ErrorCode {
    INVALID_REQUEST("INVALID_REQUEST", "분석 필수값이 누락되었거나 저장된 스펙 값이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    NOT_FOUND("NOT_FOUND", "회원 또는 현재 스펙 프로필을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    ANALYSIS_ALREADY_RUNNING("ANALYSIS_ALREADY_RUNNING", "해당 스펙의 연봉 분석이 이미 진행 중입니다.", HttpStatus.CONFLICT),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "연봉 분석 실행 이력을 저장하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
