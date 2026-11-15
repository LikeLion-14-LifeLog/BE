package com.lifelog.domain.specprofile.dto;

import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.specprofile.enums.AcademicAbilityType;

import java.math.BigDecimal;
import java.util.List;

/** CSV 분류에 사용할 원래 조건값과 해당 값의 구간을 함께 보관한다. */
public record SpecProfileSalaryClassificationData(
        String jobSubCategoryCode,
        String jobSubCategoryName,
        List<AcademicCondition> academicAbilities,
        List<LanguageCondition> languageExamScores,
        int certificateCount
) {
    /** 학력 항목 하나의 속성과 학점 구간. 학력 간 값은 서로 섞지 않는다. */
    public record AcademicCondition(
            AcademicAbilityType type, ResidentialArea location, MajorMainField majorAffiliation,
            String majorName, BigDecimal universityGrade, BigDecimal universityStandardGrade,
            SpecProfileScoreBand gradeBand
    ) {}

    /** 어학 항목 하나의 원 성적과 숫자/등급 구간. 숫자 체계가 미정인 시험은 구간이 null이다. */
    public record LanguageCondition(
            Long languageExamId, String languageExamName, BigDecimal scoreNumeric, String scoreGrade,
            SpecProfileScoreBand numericBand, String gradeBand
    ) {}
}
