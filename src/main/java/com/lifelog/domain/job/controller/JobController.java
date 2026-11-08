package com.lifelog.domain.job.controller;

import com.lifelog.domain.job.dto.JobInitResponse;
import com.lifelog.domain.job.dto.JobListResponse;
import com.lifelog.domain.job.service.JobService;
import com.lifelog.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    // TODO: 인증 도입 후 ADMIN 권한만 호출 가능하도록 제한
    @PostMapping("/api/admin/jobs/init")
    public ApiResponse<JobInitResponse> initJobs() {
        return ApiResponse.success(jobService.initFromCsv());
    }

    @GetMapping("/api/jobs")
    public ApiResponse<JobListResponse> getJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String mainCategoryCode,
            @RequestParam(required = false) String middleCategoryCode,
            @RequestParam(required = false) String subCategoryCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(jobService.search(
                keyword, code, mainCategoryCode, middleCategoryCode, subCategoryCode, page, size));
    }
}
