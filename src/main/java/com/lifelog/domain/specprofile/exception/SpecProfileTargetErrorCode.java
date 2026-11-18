package com.lifelog.domain.specprofile.exception;

import com.lifelog.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SpecProfileTargetErrorCode implements ErrorCode {
    BASE_SPEC_CHANGED("TARGET_409_1", "현재 스펙이 변경되었습니다. 변경된 스펙으로 다시 요청해 주세요.", HttpStatus.CONFLICT),
    INVALID_TARGET_STATE("TARGET_409_2", "저장된 사용자 목표 슬롯 상태가 올바르지 않습니다.", HttpStatus.CONFLICT),
    TARGET_CONDITIONS_NOT_RECORDED("TARGET_409_3", "저장된 목표의 조회 정보가 없어요. 목표 스펙을 다시 저장해 주세요.", HttpStatus.CONFLICT),
    INVALID_RECOMMENDATION("TARGET_502_1", "조건에 맞는 서로 다른 AI 대안 2개를 생성하지 못했습니다.", HttpStatus.BAD_GATEWAY),
    RECOMMENDATION_NOT_BETTER("TARGET_502_2", "종합 평가에서 더 나은 대안 2개를 확인하지 못했습니다.", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
