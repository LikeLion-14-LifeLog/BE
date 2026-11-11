package com.lifelog.domain.jobposting.controller;

import com.lifelog.domain.jobposting.dto.JobPostingDetailResponse;
import com.lifelog.domain.jobposting.dto.JobPostingListResponse;
import com.lifelog.domain.jobposting.dto.JobPostingSyncResponse;
import com.lifelog.domain.jobposting.entity.JobGroup;
import com.lifelog.domain.jobposting.entity.JobPostingRegion;
import com.lifelog.domain.jobposting.entity.JobPostingSort;
import com.lifelog.domain.jobposting.service.JobPostingService;
import com.lifelog.domain.jobposting.service.JobPostingSyncService;
import com.lifelog.global.response.ApiResponse;
import com.lifelog.global.response.SliceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "JobPosting", description = "채용공고 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingSyncService jobPostingSyncService;
    private final JobPostingService jobPostingService;

    @Operation(
            summary = "맞춤 공고 목록 조회",
            description = "모집 중이고 마감일이 지나지 않은 공고(상시 채용 포함)를 직무 그룹·지역으로 걸러 조회합니다. "
                    + "jobGroup을 지정하지 않으면 그룹이 정해지지 않은 공고까지 모두 포함합니다. "
                    + "tabCounts.recommended는 필터 적용 전 모집 중 공고 수, bookmarked는 내가 저장한 공고 수입니다."
    )
    @GetMapping("/job-postings")
    public ApiResponse<SliceResponse<JobPostingListResponse>> getJobPostings(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "직무 그룹 (없으면 전체)", example = "BACKEND")
            @RequestParam(required = false) JobGroup jobGroup,
            @Parameter(description = "지역 (없으면 전체)", example = "SEOUL")
            @RequestParam(required = false) JobPostingRegion region,
            @Parameter(description = "정렬 (DEADLINE: 마감 임박순, POPULAR: 저장 많은 순)", example = "DEADLINE")
            @RequestParam(defaultValue = "DEADLINE") JobPostingSort sort,
            @Parameter(description = "페이지 번호 (1부터 시작)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기 (1~100)", example = "6")
            @RequestParam(defaultValue = "6") int size
    ) {
        return ApiResponse.success(jobPostingService.search(memberId, jobGroup, region, sort, page, size));
    }

    @Operation(
            summary = "공고 상세 조회",
            description = "공고 상세를 조회합니다. 마감된 공고(status=CLOSE)나 마감일이 지난 공고도 조회할 수 있습니다. "
                    + "sections는 값이 있는 섹션만 포함합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 공고 (JOB_POSTING_404)")
    })
    @GetMapping("/job-postings/{jobPostingId}")
    public ApiResponse<JobPostingDetailResponse> getJobPosting(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "채용공고 ID", example = "101")
            @PathVariable Long jobPostingId
    ) {
        return ApiResponse.success(jobPostingService.getDetail(memberId, jobPostingId));
    }

    @Operation(
            summary = "[관리자] 채용공고 수집",
            description = "서울시 일자리포털 채용정보 전체를 받아 등록/갱신합니다. "
                    + "원본에서 사라졌거나 마감일이 지난 공고는 마감(CLOSE) 처리합니다. "
                    + "외부 API 호출이 하나라도 실패하면 아무것도 저장하지 않고 502를 반환합니다. "
                    + "호출은 성공했지만 전체 건수보다 적게 받았으면 받은 공고만 저장하고 마감 처리는 건너뜁니다(complete=false). "
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
