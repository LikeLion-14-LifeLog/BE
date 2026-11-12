package com.lifelog.global.infra.seoul;

// 서울 열린데이터 호출 실패 (네트워크·HTTP 오류, INFO-000 외 결과 코드, 인증키 미설정)
public class SeoulOpenApiException extends RuntimeException {

    public SeoulOpenApiException(String message) {
        super(message);
    }
}
