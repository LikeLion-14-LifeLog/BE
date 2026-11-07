package com.lifelog.domain.languageexam.exception;

import com.lifelog.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LanguageExamErrorCode implements ErrorCode {
    INVALID_KEYWORD("LANGUAGE_EXAM_400", "검색어는 20자 이하로 입력해주세요.", HttpStatus.BAD_REQUEST),
    CSV_READ_FAILED("LANGUAGE_EXAM_500_1", "어학시험 데이터 파일을 읽을 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_CSV_FORMAT("LANGUAGE_EXAM_500_2", "어학시험 데이터 파일 형식이 올바르지 않습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
