package com.lifelog.domain.specprofile.service.keep;

import com.lifelog.domain.major.dto.MajorCsvRow;
import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.dto.KeepSalaryAccumulatedSpec.*;
import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSample.MatchEvidence;
import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData.*;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.mapper.KeepSalaryCodeMapper;
import com.lifelog.domain.specprofile.mapper.KeepSalaryCodeMapper.Codebook;
import com.lifelog.global.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Component
public class KeepSalaryConditionMatcher {
    /**
     * Record 순회 전에 필수 조건을 검증하고 동일 학위·계열·전공 중분류 코드 집합을 준비한다.
     * @param data 점수 구간을 계산한 사용자 조건
     * @param codebook 검증된 코드 사전
     * @return 행별 DB·전공 전체 검색이 필요 없는 비교 조건
     */
    public PreparedCriteria prepare(SpecProfileSalaryClassificationData data, Codebook codebook) {
        log.info("[KeepSalaryConditionMatcher] 조건 준비 | prepare() - START");
        if (data == null || data.jobSubCategoryCode() == null || !data.jobSubCategoryCode().matches("\\d{3}")
                || data.academicAbilities() == null || data.languageExamScores() == null || data.certificateCount() < 0) throw invalid();
        List<PreparedAcademic> academics = new ArrayList<>();
        for (AcademicCondition condition : data.academicAbilities()) {
            if (condition == null || condition.type() == null || Set.of("HIGH_SCHOOL", "ETC").contains(condition.type().name())
                    || (condition.universityGrade() != null && condition.gradeBand() == null)
                    || (condition.universityGrade() == null) != (condition.universityStandardGrade() == null)) throw invalid();
            academics.add(new PreparedAcademic(condition, prepareMajorCodes(condition, codebook)));
        }
        for (LanguageCondition condition : data.languageExamScores()) {
            if (condition == null || KeepSalaryCodeMapper.examType(condition.languageExamName()).equals("OTHER")) throw invalid();
            String type = KeepSalaryCodeMapper.examType(condition.languageExamName());
            if (type.equals("NONE")) {
                if (data.languageExamScores().size() != 1 || condition.scoreNumeric() != null || condition.gradeBand() != null) throw invalid();
            } else if ((condition.scoreNumeric() != null && condition.numericBand() == null)
                    || (condition.scoreGrade() != null && !condition.scoreGrade().isBlank() && condition.gradeBand() == null)
                    || (condition.numericBand() == null && condition.gradeBand() == null)) throw invalid();
        }
        log.info("[KeepSalaryConditionMatcher] 조건 준비 | prepare() - END");
        return new PreparedCriteria(data, List.copyOf(academics), codebook);
    }

    /**
     * 누적 직무·학력 필수 조건을 검사하고 학점·어학·자격증의 유사도와 직접 일치 수를 계산한다.
     * @param criteria 사전 준비한 사용자 조건
     * @param spec 현재 시점까지의 누적 스펙
     * @param current 해당 연도 일반 컬럼
     * @param year 후보 연도
     * @return 적격 여부와 일치 근거
     */
    public MatchResult match(PreparedCriteria criteria, KeepSalaryAccumulatedSpec spec, Map<String, String> current, int year) {
        log.debug("[KeepSalaryConditionMatcher] 조건 비교 | match() - START | year: {}", year);
        List<MatchEvidence> evidence = new ArrayList<>();
        int direct = 0;
        int similarity = 0;
        String desiredJob = criteria.data().jobSubCategoryCode();
        List<JobObservation> jobs = spec.jobs().stream().filter(job -> desiredJob.equals(
                criteria.codebook().jobCategories().get(job.code()))).toList();
        if (jobs.isEmpty()) return rejected("JOB_SUB_CATEGORY");
        boolean directJob = jobs.stream().anyMatch(job -> job.direct() && job.year() == year);
        if (directJob) direct++;
        evidence.add(new MatchEvidence("JOB_SUB_CATEGORY", jobs.stream().mapToInt(JobObservation::year).max().orElseThrow()));
        for (int i = 0; i < criteria.academics().size(); i++) {
            PreparedAcademic condition = criteria.academics().get(i);
            List<AcademicObservation> matches = spec.academics().stream().filter(value -> academicMatches(condition, value)).toList();
            if (matches.isEmpty()) return rejected("ACADEMIC");
            if (matches.stream().anyMatch(value -> value.direct() && value.year() == year)) direct++;
            evidence.add(new MatchEvidence("ACADEMIC_" + i, matches.stream().mapToInt(AcademicObservation::year).max().orElseThrow()));
            SimilarityMatch grade = bestGradeMatch(condition.condition(), matches, year);
            similarity += grade.score();
            if (grade.score() == 100) {
                if (grade.direct()) direct++;
                evidence.add(new MatchEvidence("GPA_BAND_" + i, grade.availableYear()));
            }
        }
        for (int i = 0; i < criteria.data().languageExamScores().size(); i++) {
            LanguageCondition condition = criteria.data().languageExamScores().get(i);
            if (KeepSalaryCodeMapper.examType(condition.languageExamName()).equals("NONE")) {
                List<String> types = spec.fields().getOrDefault("ENGLISH_TEST_TYPE", List.of());
                if (types.contains("NONE") && types.stream().allMatch(value -> value.equals("NONE"))) {
                    similarity += 100;
                    if ("NONE".equals(current.get("ENGLISH_TEST_TYPE"))) direct++;
                    evidence.add(new MatchEvidence("ENGLISH_" + i, year));
                }
                continue;
            }
            SimilarityMatch exam = bestEnglishMatch(condition, spec.exams(), year);
            similarity += exam.score();
            if (exam.score() == 100) {
                if (exam.direct()) direct++;
                evidence.add(new MatchEvidence("ENGLISH_" + i, exam.availableYear()));
            }
        }
        BigDecimal count = BigDecimal.valueOf(criteria.data().certificateCount());
        int certificateScore = certificateSimilarity(spec.fields().getOrDefault("CERTIFICATE_TOTAL_COUNT", List.of()), count);
        similarity += certificateScore;
        if (certificateScore == 100) {
            if (sameNumber(current.get("CERTIFICATE_TOTAL_COUNT"), count)) direct++;
            evidence.add(new MatchEvidence("CERTIFICATE_COUNT", year));
        }
        log.debug("[KeepSalaryConditionMatcher] 조건 비교 | match() - END | eligible: true, similarity: {}, direct: {}", similarity, direct);
        return new MatchResult(true, similarity, direct, List.copyOf(evidence), null);
    }

    /** 같은 학력 관측값의 유형·소재지·계열·전공 중분류만 필수 조건으로 비교한다. */
    private boolean academicMatches(PreparedAcademic prepared, AcademicObservation value) {
        AcademicCondition condition = prepared.condition();
        String type = condition.type().name();
        if (!(type.equals(value.type()) || type.equals("GRADUATE_SCHOOL") && value.type().startsWith("GRADUATE_SCHOOL"))) return false;
        if (condition.location() != null && !condition.location().name().equals(value.location())) return false;
        if (condition.majorAffiliation() != null && !condition.majorAffiliation().name().equals(value.field())) return false;
        if (condition.majorName() != null && !condition.majorName().isBlank() && !prepared.majorCodes().contains(value.majorCode())) return false;
        return true;
    }

    /**
     * 전공명을 사전에서 확인한 뒤 같은 학위·계열·중분류의 전공 코드로 확장한다.
     * @param condition 한 학교의 학력 조건
     * @param codebook 전공 사전
     * @return 중분류 내 전공 코드. 전공 미입력 시 빈 집합
     */
    private Set<String> prepareMajorCodes(AcademicCondition condition, Codebook codebook) {
        log.debug("[KeepSalaryConditionMatcher] 전공 중분류 준비 | prepareMajorCodes() - START");
        if (condition.majorName() == null || condition.majorName().isBlank()) {
            log.debug("[KeepSalaryConditionMatcher] 전공 중분류 준비 | prepareMajorCodes() - END | missing: true");
            return Set.of();
        }
        List<MajorCsvRow> originals = codebook.majors().values().stream().filter(row ->
                KeepSalaryCodeMapper.normalize(row.name()).equals(KeepSalaryCodeMapper.normalize(condition.majorName()))
                        && (condition.majorAffiliation() == null || condition.majorAffiliation() == row.mainField())
                        && degreeMatches(condition.type().name(), row.degreeProcess().name())).toList();
        if (originals.isEmpty() || originals.stream().anyMatch(row -> row.middleField() == null)) throw invalid();
        Set<String> result = new HashSet<>();
        for (MajorCsvRow row : codebook.majors().values()) {
            if (originals.stream().anyMatch(original -> original.degreeProcess() == row.degreeProcess()
                    && original.mainField() == row.mainField() && original.middleField() == row.middleField())) result.add(row.code());
        }
        log.debug("[KeepSalaryConditionMatcher] 전공 중분류 준비 | prepareMajorCodes() - END | codes: {}", result.size());
        return Set.copyOf(result);
    }

    /**
     * 필수 학력 조건을 통과한 동일 학교 관측값에서만 학점 유사도를 계산한다.
     * @param condition 사용자 학력
     * @param matches 필수 조건을 통과한 관측값
     * @param year 후보 연도
     * @return 같은 만점 체계에서 가장 유사한 학점. 미관측·다른 체계는 0점
     */
    private SimilarityMatch bestGradeMatch(AcademicCondition condition, List<AcademicObservation> matches, int year) {
        log.debug("[KeepSalaryConditionMatcher] 학점 유사도 | bestGradeMatch() - START");
        SimilarityMatch best = new SimilarityMatch(0, false, year);
        if (condition.gradeBand() != null) {
            for (AcademicObservation value : matches) {
                BigDecimal gpa = number(value.gpa());
                BigDecimal scale = number(value.scale());
                if (scale == null || scale.compareTo(condition.universityStandardGrade()) != 0
                        || gpa == null || gpa.signum() < 0 || gpa.compareTo(scale) > 0) continue;
                BigDecimal step = scale.compareTo(new BigDecimal("100")) == 0 ? BigDecimal.TEN : new BigDecimal("0.5");
                best = better(best, new SimilarityMatch(bandSimilarity(condition.gradeBand(), gpa, step), value.direct() && value.year() == year, value.year()));
            }
        }
        log.debug("[KeepSalaryConditionMatcher] 학점 유사도 | bestGradeMatch() - END | score: {}", best.score());
        return best;
    }

    /**
     * 같은 시험의 연결된 성적만 비교하며 다른 시험·등급·미관측은 0점으로 유지한다.
     * @param condition 사용자 어학 조건
     * @param exams 누적 시험 관측값
     * @param year 후보 연도
     * @return 가장 유사한 같은 시험 성적
     */
    private SimilarityMatch bestEnglishMatch(LanguageCondition condition, List<EnglishObservation> exams, int year) {
        log.debug("[KeepSalaryConditionMatcher] 어학 유사도 | bestEnglishMatch() - START");
        SimilarityMatch best = new SimilarityMatch(0, false, year);
        for (EnglishObservation value : exams) {
            if (!KeepSalaryCodeMapper.examType(condition.languageExamName()).equals(value.type())) continue;
            if (condition.gradeBand() != null && !condition.gradeBand().equals(KeepSalaryCodeMapper.normalize(value.score()))) continue;
            BigDecimal numeric = number(value.score());
            if (condition.numericBand() != null && (numeric == null || numeric.signum() < 0)) continue;
            int score = condition.numericBand() == null ? 100 : bandSimilarity(condition.numericBand(), numeric,
                    condition.numericBand().upperBound().subtract(condition.numericBand().lowerInclusive()));
            best = better(best, new SimilarityMatch(score, value.direct() && value.year() == year, value.year()));
        }
        log.debug("[KeepSalaryConditionMatcher] 어학 유사도 | bestEnglishMatch() - END | score: {}", best.score());
        return best;
    }

    /**
     * 숫자 구간 거리를 같은 비중의 0~100점으로 계산한다. 같은 구간은 100점이다.
     * @param band 사용자 점수 구간
     * @param value 관측 성적
     * @param step 원래 구간 간격. 4.3점제 마지막 구간도 0.5점 간격을 유지한다.
     * @return 100 / (1 + 구간 거리)의 정수 부분
     */
    private int bandSimilarity(SpecProfileScoreBand band, BigDecimal value, BigDecimal step) {
        log.debug("[KeepSalaryConditionMatcher] 구간 유사도 | bandSimilarity() - START");
        BigDecimal distance = contains(band, value) ? BigDecimal.ZERO : value.subtract(band.lowerInclusive())
                .divide(step, 0, RoundingMode.FLOOR).abs();
        int result = new BigDecimal("100").divide(BigDecimal.ONE.add(distance), 0, RoundingMode.DOWN).intValue();
        log.debug("[KeepSalaryConditionMatcher] 구간 유사도 | bandSimilarity() - END | score: {}", result);
        return result;
    }

    /**
     * 자격증 수를 합산하지 않고 누적 관측값 중 사용자 개수와 가장 가까운 값을 비교한다.
     * @param values 누적 자격증 개수
     * @param expected 사용자 보유 개수
     * @return 100 / (1 + 개수 차이)의 정수 부분. 미관측은 0점
     */
    private int certificateSimilarity(List<String> values, BigDecimal expected) {
        log.debug("[KeepSalaryConditionMatcher] 자격증 유사도 | certificateSimilarity() - START");
        int best = 0;
        for (String value : values) {
            BigDecimal actual = number(value);
            if (actual == null || actual.signum() < 0 || actual.stripTrailingZeros().scale() > 0) continue;
            int score = new BigDecimal("100").divide(BigDecimal.ONE.add(actual.subtract(expected).abs()), 0, RoundingMode.DOWN).intValue();
            best = Math.max(best, score);
        }
        log.debug("[KeepSalaryConditionMatcher] 자격증 유사도 | certificateSimilarity() - END | score: {}", best);
        return best;
    }

    /** 동일 유사도에서는 해당 연도 직접 관측, 최신 관측 순서로 근거를 선택한다. */
    private SimilarityMatch better(SimilarityMatch previous, SimilarityMatch candidate) {
        return Comparator.comparingInt(SimilarityMatch::score).thenComparing(SimilarityMatch::direct)
                .thenComparingInt(SimilarityMatch::availableYear).compare(candidate, previous) > 0 ? candidate : previous;
    }

    /** 학위별 전공 코드를 구분한다. */
    private boolean degreeMatches(String type, String degree) {
        return type.startsWith("GRADUATE_SCHOOL") ? degree.equals("GRADUATE_SCHOOL") : type.equals(degree);
    }

    /** 기존 점수 구간의 하한·상한 포함 규칙을 적용한다. */
    private boolean contains(SpecProfileScoreBand band, BigDecimal value) {
        int upper = value.compareTo(band.upperBound());
        return value.compareTo(band.lowerInclusive()) >= 0 && (upper < 0 || upper == 0 && band.upperInclusive());
    }

    /** 비어 있거나 숫자가 아닌 값은 미관측으로 취급한다. */
    private BigDecimal number(String value) {
        try { return value == null || value.isBlank() ? null : new BigDecimal(value); }
        catch (NumberFormatException exception) { return null; }
    }

    /** 숫자 표기법 차이와 무관하게 비교한다. */
    private boolean sameNumber(String value, BigDecimal expected) {
        BigDecimal actual = number(value);
        return actual != null && actual.compareTo(expected) == 0;
    }

    /** 실패 후보를 결과에 포함하지 않도록 명시적으로 반환한다. */
    private MatchResult rejected(String reason) {
        log.debug("[KeepSalaryConditionMatcher] 조건 비교 | match() - END | eligible: false");
        return new MatchResult(false, 0, 0, List.of(), reason);
    }

    /** 확인할 수 없는 조건을 임의 생략하지 않는다. */
    private CustomException invalid() {
        return new CustomException(SpecProfileAnalyzationErrorCode.INVALID_KEEP_CLASSIFICATION_CONDITION);
    }

    public record PreparedAcademic(AcademicCondition condition, Set<String> majorCodes) {}
    public record PreparedCriteria(SpecProfileSalaryClassificationData data, List<PreparedAcademic> academics, Codebook codebook) {}
    private record SimilarityMatch(int score, boolean direct, int availableYear) {}
    public record MatchResult(boolean eligible, int similarityScore, int directMatchCount, List<MatchEvidence> evidence, String rejectionReason) {}
}
