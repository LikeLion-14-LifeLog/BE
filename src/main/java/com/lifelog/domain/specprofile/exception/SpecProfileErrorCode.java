package com.lifelog.domain.specprofile.exception;

import com.lifelog.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SpecProfileErrorCode implements ErrorCode {
    INVALID_INPUT("SPEC_PROFILE_400_1", "스펙 입력값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    DUPLICATE_ID("SPEC_PROFILE_400_2", "중복된 항목이 있습니다.", HttpStatus.BAD_REQUEST),
    INVALID_MAJOR_AFFILIATION_INPUT("SPEC_PROFILE_400_3", "입력한 major의 전공계열과 실제 major의 전공계열이 맞지 않습니다", HttpStatus.BAD_REQUEST),
    CURRENT_NOT_FOUND("SPEC_PROFILE_404_1", "현재 스펙 프로필을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    REFERENCE_NOT_FOUND("SPEC_PROFILE_404_2", "참조 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CHILD_NOT_FOUND("SPEC_PROFILE_404_3", "본인 프로필의 항목을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PROFILE_LIMIT("SPEC_PROFILE_409_1", "생성 가능한 프로필 개수를 초과했습니다.", HttpStatus.CONFLICT),
    INVALID_PROFILE_COUNT("SPEC_PROFILE_500_1", "현재 스펙 프로필 개수가 올바르지 않습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    LANGUAGE_EXAM_NOT_INITIALIZED("SPEC_PROFILE_500_2", "어학 데이터 초기화가 필요합니다", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}

