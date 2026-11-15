package com.lifelog.domain.specprofile.service.keep;

import com.lifelog.domain.specprofile.dto.KeepSalaryPanelRecord;
import com.lifelog.domain.specprofile.dto.KeepSalaryPanelRecord.YearData;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeepSalaryCsvReader {
    public static final List<Integer> YEARS = List.of(2016, 2017, 2019, 2020, 2021, 2022, 2023, 2024);
    public static final List<String> FIELDS = List.of(("""
            ACADEMIC_ABILITY_TYPE ACADEMIC_ABILITY_STATUS UNIVERSITY_TYPE UNIVERSITY_FIELD
            UNIVERSITY_MAJOR_CODE UNIVERSITY_MAJOR_SUB_FIELD UNIVERSITY_LOCATION UNIVERSITY_TOTAL_CREDITS
            UNIVERSITY_GRADUATION_GPA UNIVERSITY_GPA_SCALE GRADUATE_SCHOOL_COURSE GRADUATE_SCHOOL_TYPE
            GRADUATE_SCHOOL_FIELD GRADUATE_SCHOOL_MAJOR_CODE GRADUATE_SCHOOL_MAJOR_SUB_FIELD
            GRADUATE_SCHOOL_LOCATION GRADUATE_SCHOOL_TOTAL_CREDITS GRADUATE_SCHOOL_GRADUATION_GPA
            GRADUATE_SCHOOL_GPA_SCALE GRADUATE_SCHOOL_RESEARCH_PROJECT_COUNT INTERNSHIP_COUNT
            JOB_CLUB_PARTICIPATION JOB_PREPARATION_PROGRAM_PARTICIPATION CAREER_MENTORING_PARTICIPATION
            ENGLISH_TEST_TYPE ENGLISH_TEST_SCORE OVERSEAS_TRAINING_COUNT CERTIFICATE_TOTAL_COUNT
            NATIONAL_TECHNICAL_CERTIFICATE_COUNT NATIONAL_PROFESSIONAL_CERTIFICATE_COUNT
            STATE_ACCREDITED_PRIVATE_CERTIFICATE_COUNT INTERNATIONAL_CERTIFICATE_COUNT
            DESIRED_JOB_RELATED_CERTIFICATE_COUNT KECO_CODE ANNUAL_SALARY_KRW
            """).strip().split("\\s+"));
    private final JsonMapper jsonMapper;

    /**
     * 헤더와 식별자를 검증하며 논리 Record를 하나씩 전달한다. Reader는 호출자가 닫는다.
     * @param input UTF-8로 디코딩한 CSV
     * @param consumer 한 패널의 처리 함수
     */
    public void forEachRecord(Reader input, Consumer<KeepSalaryPanelRecord> consumer) {
        log.info("[KeepSalaryCsvReader] 패널 읽기 | forEachRecord() - START");
        Set<String> ids = new HashSet<>();
        List<String> header = new ArrayList<>();
        Set<String> required = new HashSet<>(Set.of("PANEL_ID"));
        for (int year : YEARS) {
            for (String field : FIELDS) required.add(field + "_" + year);
            required.add("SPEC_HISTORY_" + year);
        }
        readTable(input, cells -> {
            if (header.isEmpty()) {
                if (new HashSet<>(cells).size() != cells.size() || !new HashSet<>(cells).equals(required)) {
                    throw invalid("필수 헤더 또는 중복 헤더 오류");
                }
                header.addAll(cells);
                return;
            }
            if (cells.size() != header.size()) throw invalid("컬럼 개수 오류");
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < header.size(); i++) row.put(header.get(i), cells.get(i).strip());
            String id = row.get("PANEL_ID");
            if (id.isBlank() || !ids.add(id)) throw invalid("패널 식별자 누락 또는 중복");
            List<YearData> years = new ArrayList<>();
            for (int year : YEARS) {
                Map<String, String> values = new LinkedHashMap<>();
                for (String field : FIELDS) values.put(field, row.get(field + "_" + year));
                years.add(new YearData(year, Map.copyOf(values), parseHistory(row.get("SPEC_HISTORY_" + year), year)));
            }
            consumer.accept(new KeepSalaryPanelRecord(id, List.copyOf(years)));
        });
        if (header.isEmpty()) throw invalid("빈 CSV");
        log.info("[KeepSalaryCsvReader] 패널 읽기 | forEachRecord() - END | count: {}", ids.size());
    }

    /**
     * 인용부호·쉼표·개행·BOM을 처리하며 CSV 논리 행을 하나씩 전달한다.
     * @param input CSV 입력
     * @param consumer 논리 행 처리 함수
     */
    public void readTable(Reader input, Consumer<List<String>> consumer) {
        log.debug("[KeepSalaryCsvReader] 테이블 읽기 | readTable() - START");
        PushbackReader reader = new PushbackReader(new BufferedReader(input), 1);
        try {
            int first = reader.read();
            if (first != -1 && first != '\uFEFF') reader.unread(first);
            List<String> row;
            while ((row = readRow(reader)) != null) {
                if (row.size() != 1 || !row.getFirst().isBlank()) consumer.accept(row);
            }
        } catch (IOException exception) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.KEEP_CSV_READ_FAILED);
        }
        log.debug("[KeepSalaryCsvReader] 테이블 읽기 | readTable() - END");
    }

    /** 인용 필드의 닫힘과 이스케이프를 검증하며 다음 논리 행을 읽는다. */
    private List<String> readRow(PushbackReader reader) throws IOException {
        log.debug("[KeepSalaryCsvReader] 행 읽기 | readRow() - START");
        List<String> cells = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closed = false;
        int ch;
        while ((ch = reader.read()) != -1) {
            if (quoted) {
                if (ch == '"') {
                    int next = reader.read();
                    if (next == '"') field.append('"');
                    else {
                        quoted = false;
                        closed = true;
                        if (next != -1) reader.unread(next);
                    }
                } else field.append((char) ch);
            } else if (ch == ',' || ch == '\n' || ch == '\r') {
                cells.add(field.toString());
                field.setLength(0);
                closed = false;
                if (ch != ',') {
                    if (ch == '\r') {
                        int next = reader.read();
                        if (next != -1 && next != '\n') reader.unread(next);
                    }
                    log.debug("[KeepSalaryCsvReader] 행 읽기 | readRow() - END");
                    return cells;
                }
            } else if (closed) {
                if (!Character.isWhitespace(ch)) throw invalid("닫는 인용부호 뒤 문자 오류");
            } else if (ch == '"') {
                if (!field.isEmpty()) throw invalid("필드 중간 인용부호 오류");
                quoted = true;
            } else field.append((char) ch);
        }
        if (quoted) throw invalid("닫히지 않은 인용부호");
        log.debug("[KeepSalaryCsvReader] 행 읽기 | readRow() - END");
        if (cells.isEmpty() && field.isEmpty() && !closed) return null;
        cells.add(field.toString());
        return cells;
    }

    /** 해당 시점의 키만 허용하고 JSON의 배열 값을 연도 없는 필드로 변환한다. */
    private Map<String, List<String>> parseHistory(String text, int year) {
        log.debug("[KeepSalaryCsvReader] 이력 해석 | parseHistory() - START");
        if (text.isBlank()) return Map.of();
        Map<?, ?> source;
        try {
            source = jsonMapper.readValue(text, Map.class);
        } catch (RuntimeException exception) {
            throw invalid("스펙 이력 JSON 오류");
        }
        if (source == null) throw invalid("스펙 이력 객체 누락");
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String suffix = "_" + year;
            if (!key.endsWith(suffix)) throw invalid("이력 연도 불일치");
            String base = key.substring(0, key.length() - suffix.length());
            if (!FIELDS.contains(base) || !(entry.getValue() instanceof List<?> list)) throw invalid("이력 필드 오류");
            List<String> values = new ArrayList<>();
            for (Object value : list) {
                if (value == null) continue;
                if (!(value instanceof String) && !(value instanceof Number)) throw invalid("이력 값 유형 오류");
                String normalized = value instanceof Number ? new BigDecimal(value.toString()).stripTrailingZeros().toPlainString() : value.toString().strip();
                if (!normalized.isBlank()) values.add(normalized);
            }
            result.put(base, List.copyOf(values));
        }
        log.debug("[KeepSalaryCsvReader] 이력 해석 | parseHistory() - END");
        return Map.copyOf(result);
    }

    /** 원본 스펙을 노출하지 않고 구조 오류를 반환한다. */
    private CustomException invalid(String reason) {
        return new CustomException(SpecProfileAnalyzationErrorCode.INVALID_KEEP_CSV_FORMAT, reason);
    }
}
