package com.lifelog.domain.specprofile;

import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.enums.AcademicAbilityStatus;
import com.lifelog.domain.specprofile.enums.AcademicAbilityType;
import com.lifelog.domain.specprofile.enums.ExperienceType;
import com.lifelog.domain.specprofile.exception.SpecProfileTargetErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileTargetMapper;
import com.lifelog.domain.specprofile.validation.SpecProfileTargetRecommendationValidator;
import com.lifelog.global.exception.CustomException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class SpecProfileTargetRecommendationValidatorTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final SpecProfileTargetMapper mapper = new SpecProfileTargetMapper(JsonMapper.builder().findAndAddModules().build());
    private final SpecProfileTargetRecommendationValidator validator = new SpecProfileTargetRecommendationValidator(FACTORY.getValidator(), mapper);

    @AfterAll
    static void 정리() {
        FACTORY.close();
    }

    @Test
    @DisplayName("목록 순서만 바꾼 준비안은 같은 대안으로 판단한다")
    void 경험순서변경_중복대안_실패() {
        // given
        SpecProfileTargetChanges first = new SpecProfileTargetChanges(null, null, null, List.of(experience("첫 프로젝트"), experience("둘째 프로젝트")));
        SpecProfileTargetChanges reordered = new SpecProfileTargetChanges(null, null, null, List.of(experience("둘째 프로젝트"), experience("첫 프로젝트")));
        // when / then
        assertThatThrownBy(() -> validator.validate(response(first, reordered), SpecProfileTargetChanges.empty()))
                .isInstanceOfSatisfying(CustomException.class, value -> assertThat(value.getErrorCode()).isEqualTo(SpecProfileTargetErrorCode.INVALID_RECOMMENDATION));
    }

    @Test
    @DisplayName("학점 소수 표기와 분석 제외 날짜만 바꾼 사용자 목표를 새 대안으로 허용하지 않는다")
    void 학점표현과날짜변경_사용자목표중복_실패() {
        // given
        SpecProfileTargetChanges user = academic("4.0", YearMonth.of(2027, 2));
        SpecProfileTargetChanges duplicate = academic("4.00", YearMonth.of(2029, 2));
        SpecProfileTargetChanges other = new SpecProfileTargetChanges(null, null, null, List.of(experience("프로젝트")));
        // when / then
        assertThatThrownBy(() -> validator.validate(response(duplicate, other), user))
                .isInstanceOfSatisfying(CustomException.class, value -> assertThat(value.getErrorCode()).isEqualTo(SpecProfileTargetErrorCode.INVALID_RECOMMENDATION));
    }

    @Test
    @DisplayName("반드시 두 개의 비어 있지 않은 서로 다른 추가 준비안을 생성해야 한다")
    void 빈대안과개수누락_실패() {
        // given
        SpecProfileTargetChanges project = new SpecProfileTargetChanges(null, null, null, List.of(experience("프로젝트")));
        // when / then
        assertThatThrownBy(() -> validator.validate(response(SpecProfileTargetChanges.empty(), project), SpecProfileTargetChanges.empty()))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> validator.validate(new SpecProfileTargetRecommendationAiResDto(List.of()), SpecProfileTargetChanges.empty()))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> validator.validate(null, SpecProfileTargetChanges.empty())).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("프롬프트에 제공하지 않은 마스터 ID는 사용하지 못한다")
    void 참조목록밖ID_추천검증_실패() {
        // given
        SpecProfileTargetChanges certificate = new SpecProfileTargetChanges(null,
                List.of(new SpecProfileTargetChanges.CertificateChange(999L, null)), null, null);
        SpecProfileTargetChanges project = new SpecProfileTargetChanges(null, null, null, List.of(experience("프로젝트")));
        // when / then
        assertThatThrownBy(() -> validator.validateReferences(response(certificate, project),
                new SpecProfileTargetReferenceResDto(List.of(), List.of(), List.of())))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("미래 월과 미정 날짜는 허용한다")
    void 미래준비안_검증_성공() {
        // given
        SpecProfileTargetChanges certificate = new SpecProfileTargetChanges(null,
                List.of(new SpecProfileTargetChanges.CertificateChange(1L, null)), null, null);
        SpecProfileTargetChanges project = new SpecProfileTargetChanges(null, null, null, List.of(experience("프로젝트")));
        // when / then
        assertThatCode(() -> validator.validate(response(certificate, project), SpecProfileTargetChanges.empty())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("대안별 평가가 중복되거나 하나라도 우수하지 않으면 거절한다")
    void 최종평가누락과탈락_실패() {
        // given
        SpecProfileTargetEvaluationAiResDto duplicate = new SpecProfileTargetEvaluationAiResDto(List.of(
                new SpecProfileTargetEvaluationAiResDto.Evaluation(2, true, "기간"),
                new SpecProfileTargetEvaluationAiResDto.Evaluation(2, true, "비용")));
        SpecProfileTargetEvaluationAiResDto notBetter = new SpecProfileTargetEvaluationAiResDto(List.of(
                new SpecProfileTargetEvaluationAiResDto.Evaluation(2, false, "장점 부족"),
                new SpecProfileTargetEvaluationAiResDto.Evaluation(3, true, "활용도")));
        // when / then
        assertThatThrownBy(() -> validator.validateEvaluation(duplicate)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> validator.validateEvaluation(notBetter)).isInstanceOfSatisfying(CustomException.class,
                value -> assertThat(value.getErrorCode()).isEqualTo(SpecProfileTargetErrorCode.RECOMMENDATION_NOT_BETTER));
    }

    private SpecProfileTargetChanges academic(String grade, YearMonth date) {
        return new SpecProfileTargetChanges(List.of(new SpecProfileTargetChanges.AcademicAbilityChange(AcademicAbilityType.UNIVERSITY,
                AcademicAbilityStatus.GRADUATED, ResidentialArea.SEOUL, new BigDecimal(grade), new BigDecimal("4.5"), date,
                MajorMainField.ENGINEERING, 1L)), null, null, null);
    }

    private SpecProfileTargetChanges.ExperienceChange experience(String title) {
        return new SpecProfileTargetChanges.ExperienceChange(ExperienceType.PROJECT, title, YearMonth.of(2099, 1),
                null, "개발 경험", false, null, null);
    }

    private SpecProfileTargetRecommendationAiResDto response(SpecProfileTargetChanges first, SpecProfileTargetChanges second) {
        return new SpecProfileTargetRecommendationAiResDto(List.of(
                new SpecProfileTargetRecommendationAiResDto.Option("첫 대안", first, "기간과 활용도"),
                new SpecProfileTargetRecommendationAiResDto.Option("둘째 대안", second, "비용과 활용도")));
    }
}
