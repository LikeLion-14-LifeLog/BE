package com.lifelog.domain.specprofile.dto;

import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import lombok.Builder;
import java.util.List;

@Builder
public record SpecProfileAnalyzationSnapshot(
        Long specProfileId,
        Integer birthYear,
        ResidentialArea residenceRegion,
        Long jobId,
        String jobName,
        List<AcademicAbilityResDto> academicAbilities,
        List<CertificateResDto> certificates,
        List<LanguageExamScoreResDto> languageExamScores,
        List<ExperienceResDto> experiences
) {}
