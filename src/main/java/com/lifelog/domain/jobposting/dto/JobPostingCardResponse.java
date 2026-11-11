package com.lifelog.domain.jobposting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lifelog.domain.company.entity.Company;
import com.lifelog.domain.jobposting.entity.JobPosting;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Schema(description = "채용공고 카드")
public record JobPostingCardResponse(
        @Schema(description = "채용공고 ID", example = "101")
        long jobPostingId,
        @Schema(description = "공고 제목", example = "주니어 백엔드 웹 개발자 (신입 정규직)")
        String title,
        @Schema(description = "기업명", example = "(주)네오커머스")
        String companyName,
        @Schema(description = "기업 규모 (원본에 없으면 null)", example = "중견기업", nullable = true)
        String companySizeLabel,
        @Schema(description = "업종 (원본에 없으면 null)", example = "이커머스", nullable = true)
        String industryLabel,
        @Schema(description = "학력 조건", example = "대졸(4년제) 이상", nullable = true)
        String educationRequirement,
        @Schema(description = "근무 지역", example = "서울 강남구", nullable = true)
        String workRegion,
        @Schema(description = "예상 연봉 하한 (연봉 모델 준비 전이라 null)", nullable = true)
        Long expectedSalaryMin,
        @Schema(description = "예상 연봉 상한 (연봉 모델 준비 전이라 null)", nullable = true)
        Long expectedSalaryMax,
        @JsonProperty("dDay")
        @Schema(description = "마감까지 남은 일수 (마감일 당일 0, 상시 채용이면 null)", example = "3", nullable = true)
        Integer dDay,
        @Schema(description = "저장 수", example = "312")
        int bookmarkCount,
        @Schema(description = "내가 저장했는지", example = "false")
        boolean bookmarked
) {

    public static JobPostingCardResponse of(JobPosting posting, boolean bookmarked, LocalDate today) {
        Company company = posting.getCompany();
        return new JobPostingCardResponse(
                posting.getId(),
                posting.getTitle(),
                company.getName(),
                // 원본에 기업 규모가 없어 현재 항상 null
                company.getSizeType(),
                company.getIndustryName(),
                posting.getEducationRequirement(),
                posting.getRegionName(),
                // TODO: 연봉 모델 도입 후 예상 연봉 계산
                null,
                null,
                posting.getClosesAt() == null ? null
                        : (int) ChronoUnit.DAYS.between(today, posting.getClosesAt().toLocalDate()),
                posting.getBookmarkCount(),
                bookmarked
        );
    }
}
