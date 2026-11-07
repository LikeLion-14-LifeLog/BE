package com.lifelog.domain.languageexam.controller;

import com.lifelog.domain.languageexam.dto.LanguageExamInitResponse;
import com.lifelog.domain.languageexam.dto.LanguageExamListResponse;
import com.lifelog.domain.languageexam.entity.LanguageType;
import com.lifelog.domain.languageexam.entity.ScoreType;
import com.lifelog.domain.languageexam.service.LanguageExamService;
import com.lifelog.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LanguageExamController {

    private final LanguageExamService languageExamService;

    // TODO: 인증 도입 후 ADMIN 권한만 호출 가능하도록 제한
    @PostMapping("/api/admin/language-exams/init")
    public ApiResponse<LanguageExamInitResponse> initLanguageExams() {
        return ApiResponse.success(languageExamService.initFromCsv());
    }

    @GetMapping("/api/language-exams")
    public ApiResponse<LanguageExamListResponse> getLanguageExams(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) LanguageType languageType,
            @RequestParam(required = false) ScoreType scoreType
    ) {
        return ApiResponse.success(languageExamService.search(keyword, languageType, scoreType));
    }
}
