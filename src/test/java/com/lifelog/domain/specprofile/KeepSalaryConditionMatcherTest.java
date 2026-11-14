package com.lifelog.domain.specprofile;

import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.dto.KeepSalaryPanelRecord.YearData;
import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData.*;
import com.lifelog.domain.specprofile.enums.AcademicAbilityType;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.domain.major.dto.MajorCsvRow;
import com.lifelog.domain.major.enums.DegreeProcess;
import com.lifelog.domain.major.enums.MajorMiddleField;
import com.lifelog.domain.major.enums.MajorSubField;
import com.lifelog.domain.specprofile.mapper.KeepSalaryCodeMapper.Codebook;
import com.lifelog.domain.specprofile.service.keep.KeepSalaryConditionMatcher;
import com.lifelog.domain.specprofile.service.keep.KeepSalarySpecAccumulator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class KeepSalaryConditionMatcherTest {
    private final KeepSalaryConditionMatcher matcher = new KeepSalaryConditionMatcher();
    private final KeepSalarySpecAccumulator accumulator = new KeepSalarySpecAccumulator();
    private final Codebook codes = new Codebook(Map.of("1332", "133", "6110", "611"), Map.of());

    @Test
    @DisplayName("과거 직무와 history에만 있는 자격증 개수를 누적해 비교하고 합산하지 않는다")
    void 누적값과중복제거_비교_성공() {
        // given
        YearData first = year(2019, Map.of("KECO_CODE", "1332", "CERTIFICATE_TOTAL_COUNT", "2"), Map.of());
        KeepSalaryAccumulatedSpec previous = accumulator.accumulate(null, first);
        YearData second = year(2020, Map.of("KECO_CODE", "6110"), Map.of("CERTIFICATE_TOTAL_COUNT", List.of("2", "3", "3")));
        KeepSalaryAccumulatedSpec accumulated = accumulator.accumulate(previous, second);
        // when
        KeepSalaryConditionMatcher.MatchResult result = match(conditions(List.of(), List.of(), 2), accumulated, second);
        // then
        assertThat(result.eligible()).isTrue();
        assertThat(result.directMatchCount()).isZero();
        assertThat(accumulated.fields().get("CERTIFICATE_TOTAL_COUNT")).containsExactly("2", "3");
        assertThat(previous.fields().get("CERTIFICATE_TOTAL_COUNT")).containsExactly("2");
        assertThat(result.similarityScore()).isEqualTo(100);
        KeepSalaryConditionMatcher.MatchResult differentCount = match(conditions(List.of(), List.of(), 5), accumulated, second);
        assertThat(differentCount.eligible()).isTrue();
        assertThat(differentCount.similarityScore()).isEqualTo(33);
        assertThatThrownBy(() -> accumulated.fields().get("CERTIFICATE_TOTAL_COUNT").add("5")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("직무가 불일치하면 다른 조건이 같아도 표본에서 제외한다")
    void 직무불일치_비교_실패() {
        // given
        YearData row = year(2020, Map.of("KECO_CODE", "6110", "CERTIFICATE_TOTAL_COUNT", "2"), Map.of());
        // when / then
        assertThat(match(conditions(List.of(), List.of(), 2), accumulator.accumulate(null, row), row).eligible()).isFalse();
    }

    @Test
    @DisplayName("서로 다른 학교의 계열과 소재지를 조합하지 않는다")
    void 학력간속성혼합_비교_실패() {
        // given
        YearData first = year(2019, Map.of("KECO_CODE", "1332", "CERTIFICATE_TOTAL_COUNT", "2",
                "UNIVERSITY_TYPE", "UNIVERSITY_4_YEAR", "UNIVERSITY_FIELD", "ENGINEERING", "UNIVERSITY_LOCATION", "SEOUL"), Map.of());
        YearData second = year(2020, Map.of("UNIVERSITY_TYPE", "UNIVERSITY_4_YEAR", "UNIVERSITY_FIELD", "SOCIAL_SCIENCES", "UNIVERSITY_LOCATION", "BUSAN"), Map.of());
        AcademicCondition condition = new AcademicCondition(AcademicAbilityType.UNIVERSITY, ResidentialArea.BUSAN, MajorMainField.ENGINEERING, null, null, null, null);
        KeepSalaryAccumulatedSpec accumulated = accumulator.accumulate(accumulator.accumulate(null, first), second);
        // when / then
        assertThat(match(conditions(List.of(condition), List.of(), 2), accumulated, second).eligible()).isFalse();
    }

    @Test
    @DisplayName("시험과 점수 연결을 잃은 다중 history는 어학 유사도를 가산하지 않고 표본은 유지한다")
    void 모호한어학이력_유사도영점_성공() {
        // given
        YearData row = year(2019, Map.of("KECO_CODE", "1332", "CERTIFICATE_TOTAL_COUNT", "2"),
                Map.of("ENGLISH_TEST_TYPE", List.of("TOEIC", "OPIC"), "ENGLISH_TEST_SCORE", List.of("875", "IH")));
        // when / then
        KeepSalaryConditionMatcher.MatchResult result = match(conditions(List.of(), List.of(toeic()), 2), accumulator.accumulate(null, row), row);
        assertThat(result.eligible()).isTrue();
        assertThat(result.similarityScore()).isEqualTo(100);
        assertThat(result.evidence()).noneMatch(evidence -> evidence.condition().startsWith("ENGLISH"));
    }

    @Test
    @DisplayName("시험 종류가 유일한 history 성적은 복원하고 인접 구간은 낮은 유사도로 유지한다")
    void 단일시험이력과구간_비교_성공() {
        // given
        YearData row = year(2019, Map.of("KECO_CODE", "1332", "CERTIFICATE_TOTAL_COUNT", "2"),
                Map.of("ENGLISH_TEST_TYPE", List.of("NONE", "TOEIC"), "ENGLISH_TEST_SCORE", List.of("875")));
        // when / then
        assertThat(match(conditions(List.of(), List.of(toeic()), 2), accumulator.accumulate(null, row), row).similarityScore()).isEqualTo(200);
        YearData wrong = year(2019, row.values(), Map.of("ENGLISH_TEST_TYPE", List.of("TOEIC"), "ENGLISH_TEST_SCORE", List.of("900")));
        KeepSalaryConditionMatcher.MatchResult result = match(conditions(List.of(), List.of(toeic()), 2), accumulator.accumulate(null, wrong), wrong);
        assertThat(result.eligible()).isTrue();
        assertThat(result.similarityScore()).isEqualTo(150);
    }

    @Test
    @DisplayName("나중에 시험을 응시했다면 과거 NONE 이력으로 미응시 유사도를 가산하지 않는다")
    void 과거미응시_유사도영점_성공() {
        // given
        YearData row = year(2019, Map.of("KECO_CODE", "1332", "CERTIFICATE_TOTAL_COUNT", "2"),
                Map.of("ENGLISH_TEST_TYPE", List.of("NONE", "TOEIC"), "ENGLISH_TEST_SCORE", List.of("875")));
        LanguageCondition none = new LanguageCondition(9L, "영어 시험 본 적 없음", null, null, null, null);
        // when / then
        KeepSalaryConditionMatcher.MatchResult result = match(conditions(List.of(), List.of(none), 2), accumulator.accumulate(null, row), row);
        assertThat(result.eligible()).isTrue();
        assertThat(result.similarityScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("만점 경계는 학점 유사도에 포함하고 다른 만점 체계는 학점만 0점으로 처리한다")
    void 학점만점체계와상한포함_비교_성공() {
        // given
        AcademicCondition condition = new AcademicCondition(AcademicAbilityType.UNIVERSITY, null, null, null,
                new BigDecimal("4"), new BigDecimal("4"), new SpecProfileScoreBand(new BigDecimal("3.5"), new BigDecimal("4"), true));
        Map<String, String> values = Map.of("KECO_CODE", "1332", "CERTIFICATE_TOTAL_COUNT", "2", "UNIVERSITY_TYPE", "UNIVERSITY_4_YEAR",
                "UNIVERSITY_GRADUATION_GPA", "4", "UNIVERSITY_GPA_SCALE", "4");
        YearData valid = year(2019, values, Map.of());
        // when / then
        assertThat(match(conditions(List.of(condition), List.of(), 2), accumulator.accumulate(null, valid), valid).similarityScore()).isEqualTo(200);
        Map<String, String> differentScale = new HashMap<>(values);
        differentScale.put("UNIVERSITY_GPA_SCALE", "4.5");
        YearData invalid = year(2019, differentScale, Map.of());
        KeepSalaryConditionMatcher.MatchResult result = match(conditions(List.of(condition), List.of(), 2), accumulator.accumulate(null, invalid), invalid);
        assertThat(result.eligible()).isTrue();
        assertThat(result.similarityScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("영어 등급은 같은 시험과 같은 등급에만 유사도를 가산한다")
    void 어학등급과시험종류_비교_성공() {
        // given
        LanguageCondition condition = new LanguageCondition(5L, "OPIC", null, "IH", null, "IH");
        YearData row = year(2019, Map.of("KECO_CODE", "1332", "CERTIFICATE_TOTAL_COUNT", "2", "ENGLISH_TEST_TYPE", "OPIC", "ENGLISH_TEST_SCORE", " ih "), Map.of());
        // when / then
        assertThat(match(conditions(List.of(), List.of(condition), 2), accumulator.accumulate(null, row), row).similarityScore()).isEqualTo(200);
        LanguageCondition otherGrade = new LanguageCondition(5L, "OPIC", null, "AL", null, "AL");
        KeepSalaryConditionMatcher.MatchResult result = match(conditions(List.of(), List.of(otherGrade), 2), accumulator.accumulate(null, row), row);
        assertThat(result.eligible()).isTrue();
        assertThat(result.similarityScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("history에만 있는 단일 학력은 복원하지만 여러 학점과 만점을 임의로 짝짓지 않는다")
    void 학력이력복원과학점연결보호_비교_성공() {
        // given
        Map<String, List<String>> history = new HashMap<>(Map.of("UNIVERSITY_TYPE", List.of("UNIVERSITY_4_YEAR"),
                "UNIVERSITY_FIELD", List.of("ENGINEERING"), "UNIVERSITY_LOCATION", List.of("SEOUL"),
                "UNIVERSITY_MAJOR_CODE", List.of("U0001"), "UNIVERSITY_GRADUATION_GPA", List.of("3.7"), "UNIVERSITY_GPA_SCALE", List.of("4.5")));
        AcademicCondition condition = new AcademicCondition(AcademicAbilityType.UNIVERSITY, ResidentialArea.SEOUL, MajorMainField.ENGINEERING, null,
                new BigDecimal("3.75"), new BigDecimal("4.5"), new SpecProfileScoreBand(new BigDecimal("3.5"), new BigDecimal("4"), false));
        YearData row = year(2019, Map.of("KECO_CODE", "1332", "CERTIFICATE_TOTAL_COUNT", "2"), history);
        // when / then
        assertThat(match(conditions(List.of(condition), List.of(), 2), accumulator.accumulate(null, row), row).similarityScore()).isEqualTo(200);
        history.put("UNIVERSITY_GRADUATION_GPA", List.of("3.7", "95"));
        history.put("UNIVERSITY_GPA_SCALE", List.of("4.5", "100"));
        YearData ambiguous = year(2019, row.values(), history);
        KeepSalaryConditionMatcher.MatchResult result = match(conditions(List.of(condition), List.of(), 2), accumulator.accumulate(null, ambiguous), ambiguous);
        assertThat(result.eligible()).isTrue();
        assertThat(result.similarityScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("전공은 같은 학위·계열·중분류로 넓히고 다른 중분류·학교 유형·지역·계열은 제외한다")
    void 전공중분류완화와필수조건_비교_성공() {
        // given
        Codebook majors = majorCodebook();
        AcademicCondition academic = new AcademicCondition(AcademicAbilityType.UNIVERSITY, ResidentialArea.SEOUL,
                MajorMainField.ENGINEERING, "소프트웨어학과", null, null, null);
        SpecProfileSalaryClassificationData data = conditions(List.of(academic), List.of(), 2);
        Map<String, String> values = new HashMap<>(Map.of("KECO_CODE", "1332", "UNIVERSITY_TYPE", "UNIVERSITY_4_YEAR",
                "UNIVERSITY_FIELD", "ENGINEERING", "UNIVERSITY_MAJOR_CODE", "U_COMPUTER", "UNIVERSITY_LOCATION", "SEOUL"));
        // when
        YearData related = year(2024, values, Map.of());
        KeepSalaryConditionMatcher.MatchResult result = matcher.match(matcher.prepare(data, majors), accumulator.accumulate(null, related), values, 2024);
        // then
        assertThat(result.eligible()).isTrue();
        assertThat(result.similarityScore()).isZero();
        for (Map.Entry<String, String> different : Map.of("UNIVERSITY_MAJOR_CODE", "U_ELECTRICAL", "UNIVERSITY_TYPE", "COLLEGE_4_YEAR",
                "UNIVERSITY_FIELD", "SOCIAL_SCIENCES", "UNIVERSITY_LOCATION", "BUSAN").entrySet()) {
            Map<String, String> changed = new HashMap<>(values);
            changed.put(different.getKey(), different.getValue());
            YearData unrelated = year(2024, changed, Map.of());
            assertThat(matcher.match(matcher.prepare(data, majors), accumulator.accumulate(null, unrelated), changed, 2024).eligible())
                    .as(different.getKey()).isFalse();
        }
        assertThat(matcher.prepare(data, majors).academics().getFirst().majorCodes())
                .containsExactlyInAnyOrder("U_SOFTWARE", "U_COMPUTER");
    }

    @Test
    @DisplayName("졸업 상태와 졸업 시기는 표본 제외나 유사도 점수에 사용하지 않는다")
    void 재학과졸업상태_비교제외_성공() {
        // given
        AcademicCondition academic = new AcademicCondition(AcademicAbilityType.UNIVERSITY, ResidentialArea.SEOUL, MajorMainField.ENGINEERING,
                null, null, null, null);
        SpecProfileSalaryClassificationData data = conditions(List.of(academic), List.of(), 2);
        Map<String, String> values = new HashMap<>(Map.of("KECO_CODE", "1332", "UNIVERSITY_TYPE", "UNIVERSITY_4_YEAR",
                "UNIVERSITY_FIELD", "ENGINEERING", "UNIVERSITY_LOCATION", "SEOUL", "ACADEMIC_ABILITY_STATUS", "ATTENDING"));
        YearData attending = year(2021, Map.copyOf(values), Map.of());
        values.put("ACADEMIC_ABILITY_STATUS", "GRADUATED");
        YearData graduated = year(2024, values, Map.of());
        // when
        KeepSalaryConditionMatcher.MatchResult before = match(data, accumulator.accumulate(null, attending), attending);
        KeepSalaryConditionMatcher.MatchResult after = match(data, accumulator.accumulate(null, graduated), graduated);
        // then
        assertThat(before.eligible()).isTrue();
        assertThat(after.eligible()).isTrue();
        assertThat(before.similarityScore()).isEqualTo(after.similarityScore());
    }

    private Codebook majorCodebook() {
        return new Codebook(codes.jobCategories(), Map.of(
                "U_SOFTWARE", new MajorCsvRow(DegreeProcess.UNIVERSITY, MajorMainField.ENGINEERING, MajorMiddleField.COMPUTER_COMMUNICATIONS,
                        MajorSubField.APPLIED_SOFTWARE_ENGINEERING, "소프트웨어학과", "U_SOFTWARE"),
                "U_COMPUTER", new MajorCsvRow(DegreeProcess.UNIVERSITY, MajorMainField.ENGINEERING, MajorMiddleField.COMPUTER_COMMUNICATIONS,
                        MajorSubField.APPLIED_SOFTWARE_ENGINEERING, "컴퓨터공학과", "U_COMPUTER"),
                "U_ELECTRICAL", new MajorCsvRow(DegreeProcess.UNIVERSITY, MajorMainField.ENGINEERING, MajorMiddleField.ELECTRICAL_ELECTRONICS,
                        MajorSubField.APPLIED_SOFTWARE_ENGINEERING, "다른학과", "U_ELECTRICAL"),
                "C_SOFTWARE", new MajorCsvRow(DegreeProcess.COLLEGE, MajorMainField.ENGINEERING, MajorMiddleField.COMPUTER_COMMUNICATIONS,
                        MajorSubField.APPLIED_SOFTWARE_ENGINEERING, "소프트웨어학과", "C_SOFTWARE")));
    }

    private LanguageCondition toeic() {
        return new LanguageCondition(1L, "TOEIC", new BigDecimal("875"), null,
                new SpecProfileScoreBand(new BigDecimal("850"), new BigDecimal("900"), false), null);
    }

    private SpecProfileSalaryClassificationData conditions(List<AcademicCondition> academics, List<LanguageCondition> exams, int count) {
        return new SpecProfileSalaryClassificationData("133", "개발", academics, exams, count);
    }

    private KeepSalaryConditionMatcher.MatchResult match(SpecProfileSalaryClassificationData conditions, KeepSalaryAccumulatedSpec spec, YearData row) {
        return matcher.match(matcher.prepare(conditions, codes), spec, row.values(), row.year());
    }

    private YearData year(int year, Map<String, String> values, Map<String, List<String>> history) {
        return new YearData(year, values, history);
    }
}
