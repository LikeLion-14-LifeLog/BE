package com.lifelog.domain.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lifelog.domain.dashboard.enums.DashboardCoreSpecType;
import com.lifelog.domain.jobposting.entity.DeadlineType;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 기존 공통 응답을 변경하지 않는 대시보드 전용 성공 응답. */
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DashboardResDto(
        @Schema(description = "HTTP 상태 코드", example = "200") int status,
        @Schema(example = "요청이 성공적으로 처리되었습니다.") String message,
        Data data
) {
    @Builder
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Data(
            @Schema(description = "최신 CURRENT 분석 실행. 이력이 없으면 null", nullable = true) Analysis analysis,
            RecommendedJobPostings recommendedJobPostings
    ) {}

    @Builder
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Analysis(
            Long runId,
            Long specProfileId,
            SpecProfileAnalyzationStatus status,
            @Schema(description = "서울 시각 기준 분석 요청 시각") LocalDateTime requestedAt,
            @Schema(description = "완료된 실행의 분석 결과. 미완료이면 null", nullable = true) Result result
    ) {}

    @Builder
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Result(
            @Schema(description = "첫 취업 기준 연차", example = "0") int annual,
            @Schema(description = "사용자 예상 초봉, 원", example = "36000000") Long expectedStartingSalary,
            @Schema(description = "비교 집단 내 상위 비율. 합격 확률이 아님", example = "35.0") BigDecimal topPercent,
            String salaryNotice,
            @Schema(description = "분석 당시 스냅샷으로 구성한 핵심 스펙, 최대 4개") List<CoreSpec> coreSpecs
    ) {}

    @Builder
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CoreSpec(DashboardCoreSpecType type, String label) {}

    @Builder
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RecommendedJobPostings(
            @Schema(description = "기존 맞춤 조건 적용 여부") boolean personalized,
            @Schema(description = "같은 조건의 모집 중 전체 공고 수") long totalCount,
            @Schema(description = "기존 마감 정렬 기준 상위 공고, 최대 3개") List<JobPostingCard> jobPostings
    ) {}

    @Builder
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record JobPostingCard(
            Long jobPostingId,
            String title,
            String companyName,
            @Schema(nullable = true) String workRegion,
            @Schema(description = "공고별 예상 연봉 하한. 기존 조회 로직에서 현재 미제공", nullable = true) Long expectedSalaryMin,
            @Schema(description = "공고별 예상 연봉 상한. 기존 조회 로직에서 현재 미제공", nullable = true) Long expectedSalaryMax,
            DeadlineType deadlineType,
            @JsonProperty("dDay") @Schema(description = "서울 기준 마감까지 일수. 마감일이 없으면 null", nullable = true) Integer dDay,
            @Schema(description = "저장된 SKILL 요건. 없으면 빈 배열") List<String> skillTags
    ) {}
}
