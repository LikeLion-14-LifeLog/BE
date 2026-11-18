package com.lifelog.domain.specprofile.dto;

import com.lifelog.domain.languageexam.entity.ScoreType;
import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.domain.major.enums.DegreeProcess;
import lombok.Builder;

import java.util.List;

/** 추천 AI가 사용할 수 있는 실제 마스터 ID와 표시 정보. 사용자 항목 PK는 포함하지 않는다. */
@Builder
public record SpecProfileTargetReferenceResDto(
        List<MajorReference> majors,
        List<CertificateReference> certificates,
        List<LanguageExamReference> languageExams
) {
    @Builder
    public record MajorReference(Long id, String name, MajorMainField mainField, DegreeProcess degreeProcess) {}
    @Builder
    public record CertificateReference(Long id, String name) {}
    @Builder
    public record LanguageExamReference(Long id, String name, ScoreType scoreType) {}
}
