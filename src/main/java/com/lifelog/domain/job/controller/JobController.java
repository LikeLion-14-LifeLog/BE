package com.lifelog.domain.job.controller;

import com.lifelog.domain.job.dto.JobInitResponse;
import com.lifelog.domain.job.dto.JobListResponse;
import com.lifelog.domain.job.service.JobService;
import com.lifelog.global.response.ApiResponse;
import com.lifelog.global.response.SliceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Job", description = "직무 API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    // TODO: 인증 도입 후 ADMIN 권한만 호출 가능하도록 제한
    @Operation(
            summary = "직무 기준 데이터 일괄 등록/갱신",
            description = "서버의 job.csv를 읽어 code 기준으로 등록/갱신합니다. 반복 호출해도 중복 생성되지 않으며 jobId는 유지됩니다. CSV에 없는 기존 직무는 삭제하지 않습니다."
    )
    @PostMapping("/admin/jobs/init")
    public ApiResponse<JobInitResponse> initJobs() {
        return ApiResponse.success(jobService.initFromCsv());
    }

    @Operation(
            summary = "직무 목록 조회",
            description = "8차 고용직업분류 기준 직무 목록을 검색합니다. 모든 조건은 AND로 적용되며 code 오름차순으로 정렬됩니다."
    )
    @GetMapping("/jobs")
    public ApiResponse<SliceResponse<JobListResponse>> getJobs(
            @Parameter(description = "직무명 검색어 (부분 일치, 대소문자 무시, 앞뒤 공백 제거 후 최대 50자)", example = "임원")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "세분류 코드 (정확히 일치)", example = "0111")
            @RequestParam(required = false) String code,
            @Parameter(description = "대분류 코드", example = "0")
            @RequestParam(required = false) String mainCategoryCode,
            @Parameter(description = "중분류 코드", example = "01")
            @RequestParam(required = false) String middleCategoryCode,
            @Parameter(description = "소분류 코드", example = "011")
            @RequestParam(required = false) String subCategoryCode,
            @Parameter(description = "페이지 번호 (1부터 시작)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기 (1~100)", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(jobService.search(
                keyword, code, mainCategoryCode, middleCategoryCode, subCategoryCode, page, size));
    }
}
