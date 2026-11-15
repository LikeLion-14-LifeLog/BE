package com.lifelog.domain.specprofile.service.keep;

import com.lifelog.domain.specprofile.dto.KeepSalaryAccumulatedSpec;
import com.lifelog.domain.specprofile.dto.KeepSalaryAccumulatedSpec.*;
import com.lifelog.domain.specprofile.dto.KeepSalaryPanelRecord.YearData;
import com.lifelog.domain.specprofile.mapper.KeepSalaryCodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Component
public class KeepSalarySpecAccumulator {
    /**
     * 이전 시점과 현재 값·이력을 합쳐 새 불변 스냅샷을 만든다. 연봉은 누적 대상에서 제외한다.
     * @param previous 이전 조사까지의 스펙
     * @param current 현재 조사 원본
     * @return 현재 시점까지 누적된 독립 스냅샷
     */
    public KeepSalaryAccumulatedSpec accumulate(KeepSalaryAccumulatedSpec previous, YearData current) {
        log.debug("[KeepSalarySpecAccumulator] 스펙 누적 | accumulate() - START | year: {}", current.year());
        Map<String, LinkedHashSet<String>> fields = new LinkedHashMap<>();
        List<AcademicObservation> academics = new ArrayList<>();
        List<EnglishObservation> exams = new ArrayList<>();
        List<JobObservation> jobs = new ArrayList<>();
        if (previous != null) {
            previous.fields().forEach((key, values) -> fields.put(key, new LinkedHashSet<>(values)));
            academics.addAll(previous.academics());
            exams.addAll(previous.exams());
            jobs.addAll(previous.jobs());
        }
        for (String key : KeepSalaryCsvReader.FIELDS) {
            if (key.equals("ANNUAL_SALARY_KRW")) continue;
            LinkedHashSet<String> values = fields.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
            for (String value : current.history().getOrDefault(key, List.of())) addValue(values, key, value);
            addValue(values, key, current.values().get(key));
        }
        /* (1) 같은 연도에 함께 저장된 속성만 직접 관측값으로 묶는다. */
        addAcademic(academics, current.values(), current.year(), true, false);
        addAcademic(academics, current.values(), current.year(), true, true);
        String type = current.values().get("ENGLISH_TEST_TYPE");
        if (type != null && !type.isBlank()) exams.add(new EnglishObservation(current.year(), true, type, current.values().getOrDefault("ENGLISH_TEST_SCORE", "")));
        String job = current.values().get("KECO_CODE");
        if (job != null && !job.isBlank()) jobs.add(new JobObservation(current.year(), true, job));
        /* (2) history만 있는 복합값은 연결이 유일할 때에만 복원한다. 직업 코드는 이미 2025 기준이다. */
        for (String code : current.history().getOrDefault("KECO_CODE", List.of())) jobs.add(new JobObservation(current.year(), false, code));
        List<String> activeExams = fields.get("ENGLISH_TEST_TYPE").stream().filter(value -> !value.equals("NONE")).toList();
        if (activeExams.size() == 1) {
            for (String score : fields.get("ENGLISH_TEST_SCORE")) exams.add(new EnglishObservation(current.year(), false, activeExams.getFirst(), score));
        }
        addUniqueAcademic(academics, fields, current.year(), false);
        addUniqueAcademic(academics, fields, current.year(), true);
        Map<String, List<String>> copied = new LinkedHashMap<>();
        fields.forEach((key, values) -> copied.put(key, List.copyOf(values)));
        KeepSalaryAccumulatedSpec result = new KeepSalaryAccumulatedSpec(Map.copyOf(copied), List.copyOf(academics), List.copyOf(exams), List.copyOf(jobs));
        log.debug("[KeepSalarySpecAccumulator] 스펙 누적 | accumulate() - END");
        return result;
    }

    /** 숫자 필드의 표기 차이를 제거한다. 코드와 숫자·등급 혼합 성적은 문자열로 보존한다. */
    private void addValue(Set<String> values, String key, String value) {
        if (value == null || value.isBlank()) return;
        if (key.equals("ENGLISH_TEST_SCORE")) {
            try { value = new BigDecimal(value).stripTrailingZeros().toPlainString(); }
            catch (NumberFormatException exception) { /* 등급 문자열은 그대로 보존한다. */ }
        }
        if (key.endsWith("_COUNT") || key.endsWith("_CREDITS") || key.endsWith("_GPA") || key.endsWith("_SCALE")) {
            try { value = new BigDecimal(value).stripTrailingZeros().toPlainString(); }
            catch (NumberFormatException exception) { return; }
        }
        values.add(value);
    }

    /** 일반 연도 컬럼으로 대학·대학원 각각의 연결된 관측값을 구성한다. */
    private void addAcademic(List<AcademicObservation> target, Map<String, String> values, int year, boolean direct, boolean graduate) {
        String prefix = graduate ? "GRADUATE_SCHOOL_" : "UNIVERSITY_";
        String type = graduate ? KeepSalaryCodeMapper.graduateType(values.get(prefix + "COURSE"))
                : KeepSalaryCodeMapper.universityType(values.get(prefix + "TYPE"));
        if (type == null) return;
        target.add(new AcademicObservation(year, direct, type, values.get(prefix + "FIELD"), values.get(prefix + "MAJOR_CODE"),
                values.get(prefix + "LOCATION"), values.get(prefix + "GRADUATION_GPA"), values.get(prefix + "GPA_SCALE")));
    }

    /** 학력의 식별·분류 필드가 모두 단일 값일 때에만 history의 조합을 복원한다. */
    private void addUniqueAcademic(List<AcademicObservation> target, Map<String, LinkedHashSet<String>> fields, int year, boolean graduate) {
        String prefix = graduate ? "GRADUATE_SCHOOL_" : "UNIVERSITY_";
        List<String> keys = List.of(prefix + (graduate ? "COURSE" : "TYPE"), prefix + "FIELD", prefix + "MAJOR_CODE", prefix + "LOCATION");
        if (keys.stream().anyMatch(key -> fields.get(key).size() != 1)) return;
        Map<String, String> unique = new HashMap<>();
        keys.forEach(key -> unique.put(key, fields.get(key).iterator().next()));
        String gpa = prefix + "GRADUATION_GPA";
        String scale = prefix + "GPA_SCALE";
        if (fields.get(gpa).size() == 1 && fields.get(scale).size() == 1) {
            unique.put(gpa, fields.get(gpa).iterator().next());
            unique.put(scale, fields.get(scale).iterator().next());
        }
        addAcademic(target, unique, year, false, graduate);
    }
}
