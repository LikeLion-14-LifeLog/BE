package com.lifelog.domain.languageexam.controller;

import com.lifelog.domain.languageexam.dto.LanguageExamInitResponse;
import com.lifelog.domain.languageexam.dto.LanguageExamListResponse;
import com.lifelog.domain.languageexam.entity.LanguageType;
import com.lifelog.domain.languageexam.entity.ScoreType;
import com.lifelog.domain.languageexam.service.LanguageExamService;
import com.lifelog.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "LanguageExam", description = "어학시험 API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LanguageExamController {

    private final LanguageExamService languageExamService;

    // TODO: 인증 도입 후 ADMIN 권한만 호출 가능하도록 제한
    @Operation(
            summary = "어학시험 기준 데이터 일괄 등록/갱신",
            description = "서버의 language_exam.csv를 읽어 language_exam_id 기준으로 등록/갱신합니다. 반복 호출해도 중복 생성되지 않으며 CSV에 없는 기존 데이터는 삭제하지 않습니다."
    )
    @PostMapping("/admin/language-exams/init")
    public ApiResponse<LanguageExamInitResponse> initLanguageExams() {
        return ApiResponse.success(languageExamService.initFromCsv());
    }

    @Operation(
            summary = "어학시험 목록 조회",
            description = "어학시험 목록을 검색합니다. 조건이 없으면 전체를 반환하며 ID 오름차순으로 정렬됩니다."
    )
    @GetMapping("/language-exams")
    public ApiResponse<LanguageExamListResponse> getLanguageExams(
            @Parameter(description = "시험명 검색어 (부분 일치, 대소문자 무시, 앞뒤 공백 제거 후 최대 20자)", example = "toeic")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "언어 종류", example = "ENGLISH")
            @RequestParam(required = false) LanguageType languageType,
            @Parameter(description = "점수 유형", example = "NUMERIC")
            @RequestParam(required = false) ScoreType scoreType
    ) {
        return ApiResponse.success(languageExamService.search(keyword, languageType, scoreType));
    }
}
