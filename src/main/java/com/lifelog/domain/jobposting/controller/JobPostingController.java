package com.lifelog.domain.jobposting.controller;

import com.lifelog.domain.jobposting.dto.JobPostingSyncResponse;
import com.lifelog.domain.jobposting.service.JobPostingSyncService;
import com.lifelog.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "JobPosting", description = "채용공고 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingSyncService jobPostingSyncService;

    @Operation(
            summary = "[관리자] 채용공고 수집",
            description = "서울시 일자리포털 채용정보 전체를 받아 등록/갱신합니다. "
                    + "원본에서 사라졌거나 마감일이 지난 공고는 마감(CLOSE) 처리합니다. "
                    + "원본을 끝까지 받지 못하면 아무것도 저장하지 않고 502를 반환합니다. "
                    + "수 분이 걸릴 수 있으며 실행 중에 다시 호출하면 409를 반환합니다. ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수집 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 정보 없음 또는 유효하지 않은 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 수집 중 (JOB_POSTING_409)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "외부 API 호출 실패 (JOB_POSTING_502)")
    })
    @PostMapping("/admin/job-postings/sync")
    public ApiResponse<JobPostingSyncResponse> syncJobPostings() {
        return ApiResponse.success(jobPostingSyncService.sync());
    }
}
