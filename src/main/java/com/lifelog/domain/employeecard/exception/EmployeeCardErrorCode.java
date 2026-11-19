package com.lifelog.domain.employeecard.exception;

import com.lifelog.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum EmployeeCardErrorCode implements ErrorCode {
    INVALID_PHOTO("EMPLOYEE_CARD_400", "사진은 10MB 이하의 JPG, PNG, WEBP 파일만 올릴 수 있습니다.", HttpStatus.BAD_REQUEST),
    GENERATION_BUSY("EMPLOYEE_CARD_429", "캐릭터 생성 요청이 많습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
