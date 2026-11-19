package com.lifelog.domain.specprofile.validation;

import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.domain.specprofile.exception.SpecProfileTargetErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileTargetMapper;
import com.lifelog.global.exception.CustomException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SpecProfileTargetRecommendationValidator {
    private final Validator validator;
    private final SpecProfileTargetMapper mapper;

    /** 사용자 목표의 날짜 순서만 검증하며 미래 월과 미정 날짜를 허용한다. */
    public void validateDates(SpecProfileTargetChanges changes) {
        boolean invalidExperience = changes.experiences().stream().anyMatch(value -> value.endedYearMonth() != null
                && value.startedYearMonth() != null && value.endedYearMonth().isBefore(value.startedYearMonth()));
        boolean invalidScore = changes.languageExamScores().stream().anyMatch(value -> value.acquiredYearMonth() != null
                && value.expiresYearMonth() != null && value.expiresYearMonth().isBefore(value.acquiredYearMonth()));
        if (invalidExperience || invalidScore) throw new CustomException(SpecProfileErrorCode.INVALID_INPUT);
    }

    /** 유효한 입력 구조로 생성된 서로 다른 대안 두 개만 허용한다. */
    public void validate(SpecProfileTargetRecommendationAiResDto response, SpecProfileTargetChanges userChanges) {
        if (response == null || !validator.validate(response).isEmpty()) fail();
        List<SpecProfileTargetRecommendationAiResDto.Option> options = response.options();
        for (SpecProfileTargetRecommendationAiResDto.Option option : options) {
            if (!option.changes().hasChanges() || mapper.sameChanges(option.changes(), userChanges)) fail();
            validateDates(option.changes());
        }
        if (mapper.sameChanges(options.get(0).changes(), options.get(1).changes())) fail();
    }

    /** 확정된 재분석 결과로 두 대안 모두 종합적으로 우수하다고 평가되었는지 확인한다. */
    public void validateEvaluation(SpecProfileTargetEvaluationAiResDto response) {
        if (response == null || !validator.validate(response).isEmpty()) fail();
        if (!response.evaluations().stream().map(SpecProfileTargetEvaluationAiResDto.Evaluation::slotNumber)
                .sorted().toList().equals(List.of(2, 3))) fail();
        if (response.evaluations().stream().anyMatch(value -> !Boolean.TRUE.equals(value.betterOverall()))) {
            throw new CustomException(SpecProfileTargetErrorCode.RECOMMENDATION_NOT_BETTER);
        }
    }

    /** AI가 제공한 ID가 프롬프트에 제공한 실제 마스터 후보에 포함되는지 검증한다. */
    public void validateReferences(SpecProfileTargetRecommendationAiResDto response, SpecProfileTargetReferenceResDto references) {
        Set<Long> majors = references.majors().stream().map(SpecProfileTargetReferenceResDto.MajorReference::id).collect(Collectors.toSet());
        Set<Long> certificates = references.certificates().stream().map(SpecProfileTargetReferenceResDto.CertificateReference::id).collect(Collectors.toSet());
        Set<Long> exams = references.languageExams().stream().map(SpecProfileTargetReferenceResDto.LanguageExamReference::id).collect(Collectors.toSet());
        for (SpecProfileTargetRecommendationAiResDto.Option option : response.options()) {
            if (option.changes().academicAbilities().stream().anyMatch(value -> !majors.contains(value.majorId()))
                    || option.changes().certificates().stream().anyMatch(value -> !certificates.contains(value.certificateId()))
                    || option.changes().languageExamScores().stream().anyMatch(value -> !exams.contains(value.languageExamId()))) fail();
        }
    }

    /** 잘못된 AI 생성 데이터를 도메인 오류로 변환한다. */
    private void fail() {
        throw new CustomException(SpecProfileTargetErrorCode.INVALID_RECOMMENDATION);
    }
}
