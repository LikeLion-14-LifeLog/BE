package com.lifelog.domain.specprofile;

import com.lifelog.domain.languageexam.entity.ScoreType;
import com.lifelog.domain.languageexam.service.LanguageExamScoreValidator;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.enums.*;
import com.lifelog.domain.specprofile.service.SpecProfileCreationPolicy;
import com.lifelog.domain.specprofile.validation.SpecProfileSaveRequestValidator;
import com.lifelog.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SpecProfileRulesTest {
    private final LanguageExamScoreValidator scores = new LanguageExamScoreValidator();
    private final SpecProfileSaveRequestValidator requests = new SpecProfileSaveRequestValidator(
            org.mockito.Mockito.mock(com.lifelog.domain.languageexam.repository.LanguageExamRepository.class), scores);
    private final SpecProfileCreationPolicy creation = new SpecProfileCreationPolicy();

    @ParameterizedTest
    @CsvSource({
        "NUMERIC,865,,true", "NUMERIC,,,false", "NUMERIC,865,AL,false", "NUMERIC,,AL,false",
        "GRADE,,AL,true", "GRADE,,,false", "GRADE,865,AL,false", "GRADE,865,,false",
        "NUMERIC_AND_GRADE,865,AL,true", "NUMERIC_AND_GRADE,865,,false",
        "NUMERIC_AND_GRADE,,AL,false", "NUMERIC_AND_GRADE,,,false",
        "NONE,,,true", "NONE,865,,false", "NONE,,AL,false"
    })
    @DisplayName("어학 점수유형에 따라 필수와 null 조합만 검사한다")
    void 어학점수_입력조합_검증(ScoreType type, BigDecimal numeric, String grade, boolean valid) {
        // given
        var score = new LanguageExamScoreSaveReqDto(null, 1L, numeric, grade, YearMonth.of(2024, 1), null);
        // when / then
        if (valid) assertThatCode(() -> scores.validate(score, type)).doesNotThrowAnyException();
        else assertThatThrownBy(() -> scores.validate(score, type)).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("시험별 점수 범위와 허용 등급은 검사하지 않는다")
    void 어학점수_범위및등급목록검증생략_성공() {
        // given
        var score = new LanguageExamScoreSaveReqDto(null, 1L, new BigDecimal("9999"),
                "임의등급", YearMonth.of(2024, 1), null);
        // when / then
        assertThatCode(() -> scores.validate(score, ScoreType.NUMERIC_AND_GRADE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("등급 필수 시험에 공백 등급은 허용하지 않는다")
    void 어학점수_실패_공백등급() {
        // given
        var score = new LanguageExamScoreSaveReqDto(null, 1L, null, " ", YearMonth.of(2024, 1), null);
        // when / then
        assertThatThrownBy(() -> scores.validate(score, ScoreType.GRADE)).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("CURRENT 하나와 TARGET 세 개의 생성 한도를 검사한다")
    void 프로필생성_개수경계_검증() {
        // given / when / then
        assertThatCode(() -> creation.validate(SpecProfileType.CURRENT, 0)).doesNotThrowAnyException();
        assertThatThrownBy(() -> creation.validate(SpecProfileType.CURRENT, 1)).isInstanceOf(CustomException.class);
        assertThatCode(() -> creation.validate(SpecProfileType.TARGET, 2)).doesNotThrowAnyException();
        assertThatThrownBy(() -> creation.validate(SpecProfileType.TARGET, 3)).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("동일 PK 또는 자격증 참조 중복을 거절한다")
    void 요청_실패_중복항목() {
        // given
        var certificate = new CertificateSaveReqDto(1L, 1L, YearMonth.of(2024, 1));
        var request = new SpecProfileTemporarySaveReqDto(1999, ResidentialArea.SEOUL, 1L,
                null, List.of(certificate, certificate), List.of(), List.of());
        // when / then
        assertThatThrownBy(() -> requests.validateDatesAndDuplicateIdsAndConditionalFields(request, LocalDate.of(2026, 11, 11)))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("미래 출생년도와 수상 필드 누락을 거절한다")
    void 요청_실패_미래년도및수상정보() {
        // given
        var future = new SpecProfileTemporarySaveReqDto(2027, ResidentialArea.SEOUL, 1L,
                null, List.of(), List.of(), List.of());
        var award = new ExperienceSaveReqDto(null, ExperienceType.CONTEST, "공모전",
                YearMonth.of(2024, 1), null, null, true, null, null);
        var missingAward = new SpecProfileTemporarySaveReqDto(1999, ResidentialArea.SEOUL, 1L,
                null, List.of(), List.of(), List.of(award));
        // when / then
        assertThatThrownBy(() -> requests.validateDatesAndDuplicateIdsAndConditionalFields(future, LocalDate.of(2026, 11, 11))).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> requests.validateDatesAndDuplicateIdsAndConditionalFields(missingAward, LocalDate.of(2026, 11, 11))).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("미래 취득월과 종료월 역전을 거절한다")
    void 요청_실패_날짜순서() {
        // given
        var future = new LanguageExamScoreSaveReqDto(null, 1L, BigDecimal.ONE, null,
                YearMonth.of(2026, 12), null);
        var reversed = new ExperienceSaveReqDto(null, ExperienceType.PROJECT, "프로젝트",
                YearMonth.of(2024, 2), YearMonth.of(2024, 1), null, false, null, null);
        // when / then
        assertThatThrownBy(() -> requests.validateDatesAndDuplicateIdsAndConditionalFields(new SpecProfileTemporarySaveReqDto(1999, ResidentialArea.SEOUL,
                1L, null, List.of(), List.of(future), List.of()), LocalDate.of(2026, 11, 11)))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> requests.validateDatesAndDuplicateIdsAndConditionalFields(new SpecProfileTemporarySaveReqDto(1999, ResidentialArea.SEOUL,
                1L, null, List.of(), List.of(), List.of(reversed)), LocalDate.of(2026, 11, 11)))
                .isInstanceOf(CustomException.class);
    }
}

