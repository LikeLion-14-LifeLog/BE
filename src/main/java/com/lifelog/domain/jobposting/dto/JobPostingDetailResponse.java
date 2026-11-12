package com.lifelog.domain.jobposting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lifelog.domain.jobposting.entity.EmploymentType;
import com.lifelog.domain.jobposting.entity.JobPosting;
import com.lifelog.domain.jobposting.entity.JobPostingPay;
import com.lifelog.domain.jobposting.entity.JobPostingRequirement;
import com.lifelog.domain.jobposting.entity.JobPostingStatus;
import com.lifelog.domain.jobposting.entity.PayStage;
import com.lifelog.domain.jobposting.entity.RequirementPriority;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Schema(description = "채용공고 상세")
public record JobPostingDetailResponse(
        @Schema(description = "채용공고 ID", example = "101")
        long jobPostingId,
        @Schema(description = "공고 제목", example = "주니어 백엔드 웹 개발자 (신입 정규직)")
        String title,
        @Schema(description = "모집 상태 (OPEN: 모집 중, CLOSE: 마감)", example = "OPEN")
        JobPostingStatus status,
        @Schema(description = "기업")
        CompanyResponse company,
        @Schema(description = "급여 (원본에 급여 정보가 없으면 null)", nullable = true)
        PayResponse pay,
        @Schema(description = "근무 지역", example = "서울 강남구", nullable = true)
        String workRegion,
        @Schema(description = "근무지 주소", example = "서울시 강남구 테헤란로 142")
        String workplaceAddress,
        @Schema(description = "고용 형태", example = "FULL_TIME")
        EmploymentType employmentType,
        @Schema(description = "고용 형태 라벨", example = "정규직")
        String employmentTypeLabel,
        @Schema(description = "학력 조건", example = "대졸(4년제) 이상", nullable = true)
        String educationRequirement,
        @Schema(description = "마감일 (상시 채용이면 null)", example = "2026-11-05", nullable = true)
        LocalDate closesAt,
        @JsonProperty("dDay")
        @Schema(description = "마감까지 남은 일수 (마감일 당일 0, 지났으면 음수, 상시 채용이면 null)", example = "3", nullable = true)
        Integer dDay,
        @Schema(description = "본문 섹션 (값이 있는 섹션만)")
        List<SectionResponse> sections,
        @Schema(description = "내가 저장했는지", example = "false")
        boolean bookmarked,
        @Schema(description = "저장 수", example = "312")
        int bookmarkCount
) {

    static final String SECTION_RESPONSIBILITIES = "주요 업무";
    static final String SECTION_REQUIREMENTS = "지원 요건";
    static final String SECTION_WORK_AND_BENEFITS = "근무 환경 및 혜택";
    static final String SECTION_APPLICATION = "지원 방법";

    @Schema(description = "기업")
    public record CompanyResponse(
            @Schema(description = "기업명", example = "(주)네오커머스")
            String name,
            @Schema(description = "업종 (원본에 없으면 null)", example = "이커머스", nullable = true)
            String industryLabel
    ) {
    }

    @Schema(description = "급여")
    public record PayResponse(
            @Schema(description = "원본 급여 문구", example = "연봉 3,600만 ~ 4,000만 원")
            String text,
            @Schema(description = "급여 하한 (금액이 명시된 경우만, 회사 내규·협의면 null)", example = "36000000", nullable = true)
            Long amountMin,
            @Schema(description = "급여 상한 (금액이 명시된 경우만, 회사 내규·협의면 null)", example = "40000000", nullable = true)
            Long amountMax
    ) {
    }

    @Schema(description = "본문 섹션")
    public record SectionResponse(
            @Schema(description = "섹션 제목", example = "주요 업무")
            String title,
            @Schema(description = "섹션 내용", example = "...")
            String content
    ) {
    }

    public static JobPostingDetailResponse of(JobPosting posting, boolean bookmarked, LocalDate today) {
        return new JobPostingDetailResponse(
                posting.getId(),
                posting.getTitle(),
                posting.getStatus(),
                new CompanyResponse(posting.getCompany().getName(), posting.getCompany().getIndustryName()),
                pay(posting.getPays()),
                posting.getRegionName(),
                posting.getWorkplaceAddress(),
                posting.getEmploymentType(),
                posting.getEmploymentType().getLabel(),
                posting.getEducationRequirement(),
                posting.getClosesAt() == null ? null : posting.getClosesAt().toLocalDate(),
                posting.daysUntilClose(today),
                sections(posting),
                bookmarked,
                posting.getBookmarkCount()
        );
    }

    // 현재 급여(CURRENT)를 우선, 없으면 첫 번째 급여
    private static PayResponse pay(List<JobPostingPay> pays) {
        return pays.stream()
                .filter(pay -> pay.getStage() == PayStage.CURRENT)
                .findFirst()
                .or(() -> pays.stream().findFirst())
                .map(pay -> new PayResponse(pay.getDescription(), pay.getAmountMin(), pay.getAmountMax()))
                .orElse(null);
    }

    // 값이 있는 섹션만 순서대로
    private static List<SectionResponse> sections(JobPosting posting) {
        List<SectionResponse> sections = new ArrayList<>();
        addSection(sections, SECTION_RESPONSIBILITIES,
                posting.getResponsibilities() != null ? posting.getResponsibilities() : posting.getDescription());
        addSection(sections, SECTION_REQUIREMENTS, requirements(posting.getRequirements()));
        addSection(sections, SECTION_WORK_AND_BENEFITS, joinLines(posting.getWorkSchedule(), posting.getBenefits()));
        addSection(sections, SECTION_APPLICATION, posting.getApplicationMethod());
        return sections;
    }

    // "학력: 대졸(4년)", "자격증: 정보처리기사", "우대 사항: ..." 형식으로 한 줄씩
    private static String requirements(List<JobPostingRequirement> requirements) {
        return requirements.stream()
                .map(requirement -> requirement.getPriority() == RequirementPriority.PREFERRED
                        ? "우대 사항: " + requirement.getContent()
                        : requirement.getType().getLabel() + ": " + requirement.getContent())
                .collect(Collectors.joining("\n"));
    }

    private static void addSection(List<SectionResponse> sections, String title, String content) {
        if (content != null && !content.isBlank()) {
            sections.add(new SectionResponse(title, content));
        }
    }

    private static String joinLines(String... values) {
        return Stream.of(values)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("\n"));
    }
}
