package com.lifelog.domain.dashboard;

import com.lifelog.domain.dashboard.dto.DashboardResDto;
import com.lifelog.domain.dashboard.mapper.DashboardMapper;
import com.lifelog.domain.jobposting.dto.JobPostingCardResponse;
import com.lifelog.domain.jobposting.entity.*;
import com.lifelog.domain.specprofile.dto.AcademicAbilityResDto;
import com.lifelog.domain.specprofile.dto.CertificateResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnnualSalaryAnalyzationResDto.AppliedSpecs;
import com.lifelog.domain.specprofile.enums.AcademicAbilityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardMapperTest {
    private final DashboardMapper mapper = new DashboardMapper();

    @Test
    @DisplayName("대표 학력과 같은 학점을 사용하며 같은 단계의 학력·자격증은 스냅샷 순서로 선택한다")
    void 핵심스펙_대표학력과동일학점_성공() {
        // given
        AppliedSpecs specs = new AppliedSpecs(null, null, null, "백엔드 개발자", List.of(
                AcademicAbilityResDto.builder().type(AcademicAbilityType.UNIVERSITY).majorName("학부 전공")
                        .universityGrade(new BigDecimal("4.3")).universityStandardGrade(new BigDecimal("4.5")).build(),
                AcademicAbilityResDto.builder().type(AcademicAbilityType.GRADUATE_SCHOOL_MASTER).majorName("석사 전공")
                        .universityGrade(new BigDecimal("3.80")).universityStandardGrade(new BigDecimal("4.50")).build(),
                AcademicAbilityResDto.builder().type(AcademicAbilityType.GRADUATE_SCHOOL_MASTER).majorName("다른 석사").build()),
                List.of(CertificateResDto.builder().certificateName("SQLD").build(),
                        CertificateResDto.builder().certificateName("정보처리기사").build()), List.of(), List.of());
        // when
        List<DashboardResDto.CoreSpec> chips = mapper.toCoreSpecs(specs);
        // then
        assertThat(chips).extracting(DashboardResDto.CoreSpec::label)
                .containsExactly("대학원 석사 · 석사 전공", "학점 3.8 / 4.5", "SQLD 취득", "희망 직무 백엔드 개발자");
    }

    @Test
    @DisplayName("학점 일부가 없으면 하위 학력의 학점을 대신 쓰지 않으며 빈 스펙은 칩을 만들지 않는다")
    void 누락스펙_칩생략_성공() {
        // given
        AppliedSpecs partial = new AppliedSpecs(null, null, null, " ", List.of(
                AcademicAbilityResDto.builder().type(AcademicAbilityType.GRADUATE_SCHOOL_DOCTORAL).build(),
                AcademicAbilityResDto.builder().type(AcademicAbilityType.UNIVERSITY)
                        .universityGrade(new BigDecimal("4.0")).universityStandardGrade(new BigDecimal("4.5")).build()),
                List.of(CertificateResDto.builder().certificateName(" ").build()), List.of(), List.of());
        // when / then
        assertThat(mapper.toCoreSpecs(partial)).extracting(DashboardResDto.CoreSpec::label).containsExactly("대학원 박사");
        assertThat(mapper.toCoreSpecs(new AppliedSpecs(null, null, null, null, List.of(), List.of(), List.of(), List.of()))).isEmpty();
    }

    @Test
    @DisplayName("기술 요건만 순서대로 반환하고 공백과 중복을 정리하되 원문을 기술명으로 분해하지 않는다")
    void 기술태그_원문순서와중복처리_성공() {
        // given
        JobPosting posting = JobPosting.builder().deadlineType(DeadlineType.UNTIL_FILLED).build();
        posting.addRequirement(requirement(RequirementType.SKILL, " Spring Boot ", 3));
        posting.addRequirement(requirement(RequirementType.SKILL, "Java / Kotlin", 1));
        posting.addRequirement(requirement(RequirementType.CERTIFICATE, "SQLD", 0));
        posting.addRequirement(requirement(RequirementType.SKILL, "Spring Boot", 4));
        posting.addRequirement(requirement(RequirementType.SKILL, " ", 2));
        JobPostingCardResponse card = new JobPostingCardResponse(7L, "제목", JobPostingStatus.OPEN,
                "기업", null, null, null, null, null, null, null, 0, false);
        // when
        DashboardResDto.JobPostingCard result = mapper.toJobPostingResDto(card, posting);
        // then
        assertThat(result.skillTags()).containsExactly("Java / Kotlin", "Spring Boot");
        assertThat(result.deadlineType()).isEqualTo(DeadlineType.UNTIL_FILLED);
        assertThat(result.expectedSalaryMin()).isNull();
    }

    private JobPostingRequirement requirement(RequirementType type, String content, int order) {
        return JobPostingRequirement.builder().type(type).content(content).sortOrder((short) order)
                .priority(RequirementPriority.REQUIRED).build();
    }
}
