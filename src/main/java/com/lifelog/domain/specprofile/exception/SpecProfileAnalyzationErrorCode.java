package com.lifelog.domain.specprofile.exception;

import com.lifelog.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SpecProfileAnalyzationErrorCode implements ErrorCode {
    RESULT_SAVE_FAILED("RESULT_SAVE_FAILED", "연봉 분석 결과를 저장하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_SALARY_STATISTICS("INVALID_SALARY_STATISTICS", "연봉 통계 표본이 유효하지 않습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_AI_RESPONSE("INVALID_AI_RESPONSE", "연봉 예측 응답이 올바르지 않습니다.", HttpStatus.BAD_GATEWAY),
    ANNUAL_SALARY_PROMPT_BUILD_FAILED("ANNUAL_SALARY_PROMPT_BUILD_FAILED", "연봉 분석 프롬프트를 생성하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    KEEP_CSV_READ_FAILED("KEEP_CSV_READ_FAILED", "연봉 비교 CSV를 읽을 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEEP_CSV_FORMAT("INVALID_KEEP_CSV_FORMAT", "연봉 비교 데이터 형식이 올바르지 않습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEEP_CLASSIFICATION_CONDITION("INVALID_KEEP_CLASSIFICATION_CONDITION", "CSV에서 검증할 수 없는 분류 조건입니다.", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_MATCHED_SAMPLES("INSUFFICIENT_MATCHED_SAMPLES", "조건에 맞는 연봉 표본이 10개 미만입니다.", HttpStatus.BAD_REQUEST),
    RUN_NOT_FOUND("RUN_NOT_FOUND", "분석 실행 이력을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_SNAPSHOT("INVALID_SNAPSHOT", "저장된 분석 스냅샷을 읽을 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_CLASSIFICATION_SCORE("INVALID_CLASSIFICATION_SCORE", "분류용 학점 또는 어학 점수가 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST("INVALID_REQUEST", "분석 필수값이 누락되었거나 저장된 스펙 값이 유효하지 않습니다.", HttpStatus.BAD_REQUEST),
    NOT_FOUND("NOT_FOUND", "회원 또는 현재 스펙 프로필을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    ANALYSIS_ALREADY_RUNNING("ANALYSIS_ALREADY_RUNNING", "해당 스펙의 연봉 분석이 이미 진행 중입니다.", HttpStatus.CONFLICT),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "연봉 분석 실행 이력을 저장하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
