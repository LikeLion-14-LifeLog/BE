package com.lifelog.global.infra.openai;

import com.lifelog.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OpenApiErrorCode implements ErrorCode {
    OPEN_API_KEY_NOT_CONFIGURED("OPEN_API_KEY_NOT_CONFIGURED", "OpenAI 인증키가 설정되지 않았습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    OPEN_API_INPUT_TOO_LARGE("OPEN_API_INPUT_TOO_LARGE", "연봉 분석 입력이 허용 크기를 초과했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    OPEN_API_REQUEST_FAILED("OPEN_API_REQUEST_FAILED", "OpenAI 요청에 실패했습니다.", HttpStatus.BAD_GATEWAY),
    OPEN_API_RATE_LIMITED("OPEN_API_RATE_LIMITED", "OpenAI 요청량 제한을 초과했습니다. 잠시 후 다시 요청해 주세요.", HttpStatus.TOO_MANY_REQUESTS),
    OPEN_API_QUOTA_EXCEEDED("OPEN_API_QUOTA_EXCEEDED", "OpenAI API 잔액 또는 사용 한도에 도달했습니다. 결제 및 사용 한도를 확인해 주세요.", HttpStatus.SERVICE_UNAVAILABLE),
    OPEN_API_TIMEOUT("OPEN_API_TIMEOUT", "OpenAI 응답 대기 시간을 초과했습니다.", HttpStatus.GATEWAY_TIMEOUT),
    OPEN_API_REFUSED("OPEN_API_REFUSED", "OpenAI가 분석 요청을 거절했습니다.", HttpStatus.BAD_GATEWAY),
    OPEN_API_INCOMPLETE_RESPONSE("OPEN_API_INCOMPLETE_RESPONSE", "OpenAI 분석 응답이 완료되지 않았습니다.", HttpStatus.BAD_GATEWAY),
    OPEN_API_INVALID_RESPONSE("OPEN_API_INVALID_RESPONSE", "OpenAI 응답 형식이 올바르지 않습니다.", HttpStatus.BAD_GATEWAY),
    OPEN_API_INTERRUPTED("OPEN_API_INTERRUPTED", "OpenAI 요청 처리가 중단되었습니다.", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
