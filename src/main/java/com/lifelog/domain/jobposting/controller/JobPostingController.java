package com.lifelog.domain.jobposting.controller;

import com.lifelog.domain.jobposting.dto.JobPostingBookmarkListResponse;
import com.lifelog.domain.jobposting.dto.JobPostingBookmarkResponse;
import com.lifelog.domain.jobposting.dto.JobPostingDetailResponse;
import com.lifelog.domain.jobposting.dto.JobPostingJobCategoryListResponse;
import com.lifelog.domain.jobposting.dto.JobPostingListResponse;
import com.lifelog.domain.jobposting.dto.JobPostingSyncStatusResponse;
import com.lifelog.domain.jobposting.entity.JobPostingRegion;
import com.lifelog.domain.jobposting.entity.JobPostingSort;
import com.lifelog.domain.jobposting.service.JobPostingBookmarkService;
import com.lifelog.domain.jobposting.service.JobPostingCategoryService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final JobPostingBookmarkService jobPostingBookmarkService;
    private final JobPostingCategoryService jobPostingCategoryService;

    @Operation(
            summary = "맞춤 공고 목록 조회",
            description = "모집 중이고 마감일이 지나지 않은 공고(상시 채용 포함) 중 내 희망 직무와 소분류가 같고 "
                    + "최소 학력이 내 학력 이하인 공고를 조회합니다. 희망 직무가 없으면 전체 공고를 보여주고 personalized=false입니다. "
                    + "jobCategory(직무 소분류, GET /api/job-postings/job-categories의 code)·region은 맞춤 조건 위에 추가로 적용합니다. "
                    + "tabCounts.recommended는 맞춤 조건까지 적용한 공고 수(jobCategory·region 제외), bookmarked는 내가 저장한 공고 수입니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "page·size 범위 오류 또는 jobCategory·region·sort 형식 오류")
    })
    @GetMapping("/job-postings")
    public ApiResponse<SliceResponse<JobPostingListResponse>> getJobPostings(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "직무 소분류 코드 3자리 (한국고용직업분류 2025, 없으면 전체)", example = "133")
            @RequestParam(required = false) String jobCategory,
            @Parameter(description = "지역 (없으면 전체)", example = "SEOUL")
            @RequestParam(required = false) JobPostingRegion region,
            @Parameter(description = "정렬 (DEADLINE: 마감 임박순, POPULAR: 저장 많은 순)", example = "DEADLINE")
            @RequestParam(defaultValue = "DEADLINE") JobPostingSort sort,
            @Parameter(description = "페이지 번호 (1부터 시작)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기 (1~100)", example = "6")
            @RequestParam(defaultValue = "6") int size
    ) {
        return ApiResponse.success(jobPostingService.search(memberId, jobCategory, region, sort, page, size));
    }

    @Operation(
            summary = "공고 직무 필터용 소분류 검색",
            description = "공고 목록의 직무 필터(jobCategory)로 쓸 직무 소분류(한국고용직업분류 2025, 140개)를 검색합니다. "
                    + "검색형 선택 UI용이며, 검색어가 소분류명 또는 그 아래 세분류 직무명에 포함되면 해당 소분류를 반환합니다(대소문자 무시). "
                    + "검색어가 없으면 전체를 반환하며 code 오름차순입니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "검색어 50자 초과 (JOB_POSTING_400)")
    })
    @GetMapping("/job-postings/job-categories")
    public ApiResponse<JobPostingJobCategoryListResponse> getJobCategories(
            @Parameter(description = "검색어 (부분 일치, 앞뒤 공백 제거 후 최대 50자)", example = "소프트웨어")
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(jobPostingCategoryService.search(keyword));
    }

    @Operation(
            summary = "저장한 공고 목록 조회",
            description = "내가 저장한 공고를 최근 저장 순으로 조회합니다. 마감된 공고도 포함하며, "
                    + "응답 형식은 맞춤 공고 목록에서 tabCounts만 빠진 형태입니다."
    )
    @GetMapping("/job-postings/bookmarks")
    public ApiResponse<SliceResponse<JobPostingBookmarkListResponse>> getBookmarkedJobPostings(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "페이지 번호 (1부터 시작)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기 (1~100)", example = "6")
            @RequestParam(defaultValue = "6") int size
    ) {
        return ApiResponse.success(jobPostingBookmarkService.getBookmarks(memberId, page, size));
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
            summary = "공고 저장",
            description = "공고를 저장합니다. 이미 저장한 공고여도 성공하며 저장 수는 늘지 않습니다. 마감된 공고도 저장할 수 있습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장됨"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 공고 (JOB_POSTING_404)")
    })
    @PostMapping("/job-postings/{jobPostingId}/bookmark")
    public ApiResponse<JobPostingBookmarkResponse> bookmark(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "채용공고 ID", example = "101")
            @PathVariable Long jobPostingId
    ) {
        return ApiResponse.success(jobPostingBookmarkService.bookmark(memberId, jobPostingId));
    }

    @Operation(
            summary = "공고 저장 취소",
            description = "공고 저장을 취소합니다. 저장하지 않은 공고여도 성공하며 저장 수는 줄지 않습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장 취소됨"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "없는 공고 (JOB_POSTING_404)")
    })
    @DeleteMapping("/job-postings/{jobPostingId}/bookmark")
    public ApiResponse<JobPostingBookmarkResponse> unbookmark(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "채용공고 ID", example = "101")
            @PathVariable Long jobPostingId
    ) {
        return ApiResponse.success(jobPostingBookmarkService.unbookmark(memberId, jobPostingId));
    }

    @Operation(
            summary = "[관리자] 채용공고 수집 시작",
            description = "서울시 일자리포털 채용정보 전체 수집을 백그라운드에서 시작하고 바로 202를 반환합니다. "
                    + "결과는 GET /api/admin/job-postings/sync/status로 확인합니다. "
                    + "원본에서 사라졌거나 마감일이 지난 공고는 마감(CLOSE) 처리합니다. "
                    + "외부 API 호출이 하나라도 실패하면 아무것도 저장하지 않고 상태가 FAILED(errorCode JOB_POSTING_502)가 됩니다. "
                    + "호출은 성공했지만 전체 건수보다 적게 받았으면 받은 공고만 저장하고 마감 처리는 건너뜁니다(complete=false). "
                    + "실행 중에 다시 호출하면 409를 반환합니다. ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "수집 시작 (data는 시작 시점 상태)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 정보 없음 또는 유효하지 않은 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 수집 중 (JOB_POSTING_409)")
    })
    @PostMapping("/admin/job-postings/sync")
    public ResponseEntity<ApiResponse<JobPostingSyncStatusResponse>> syncJobPostings() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(jobPostingSyncService.start()));
    }

    @Operation(
            summary = "[관리자] 채용공고 수집 상태 조회",
            description = "마지막 수집 실행의 상태(IDLE·RUNNING·SUCCEEDED·FAILED), 시작·종료 시각, 결과 또는 실패 에러 코드를 반환합니다. "
                    + "상태는 서버 메모리에 보관하므로 서버를 재시작하면 IDLE입니다. ADMIN 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 정보 없음 또는 유효하지 않은 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음")
    })
    @GetMapping("/admin/job-postings/sync/status")
    public ApiResponse<JobPostingSyncStatusResponse> getSyncStatus() {
        return ApiResponse.success(jobPostingSyncService.getStatus());
    }
}
