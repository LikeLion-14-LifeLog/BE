package com.lifelog.domain.jobposting.exception;

import com.lifelog.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum JobPostingErrorCode implements ErrorCode {
    JOB_POSTING_NOT_FOUND("JOB_POSTING_404", "채용공고를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    SYNC_IN_PROGRESS("JOB_POSTING_409", "채용공고 수집이 이미 실행 중입니다.", HttpStatus.CONFLICT),
    EXTERNAL_API_FAILED("JOB_POSTING_502", "외부 채용공고 API 호출에 실패했습니다.", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
