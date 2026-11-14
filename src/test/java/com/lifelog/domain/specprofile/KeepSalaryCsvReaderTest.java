package com.lifelog.domain.specprofile;

import com.lifelog.domain.specprofile.dto.KeepSalaryPanelRecord;
import com.lifelog.domain.specprofile.service.keep.KeepSalaryCsvReader;
import com.lifelog.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class KeepSalaryCsvReaderTest {
    private final KeepSalaryCsvReader reader = new KeepSalaryCsvReader(JsonMapper.builder().build());

    @Test
    @DisplayName("CSV 인용부호 안의 JSON과 쉼표 및 줄바꿈을 하나의 셀로 읽는다")
    void 인용필드와BOM_파싱_성공() {
        // given
        String csv = "\uFEFFa,b\r\n\"{\"\"key\"\": [1,2]}\",\"첫째\n둘째\"\r\n";
        List<List<String>> rows = new ArrayList<>();
        // when
        reader.readTable(new StringReader(csv), rows::add);
        // then
        assertThat(rows).containsExactly(List.of("a", "b"), List.of("{\"key\": [1,2]}", "첫째\n둘째"));
    }

    @Test
    @DisplayName("닫히지 않은 인용부호와 잘못된 헤더는 거절한다")
    void 잘못된구조_읽기_실패() {
        // given / when / then
        assertThatThrownBy(() -> reader.readTable(new StringReader("\"abc"), row -> {})).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> reader.forEachRecord(new StringReader("PANEL_ID,PANEL_ID\n1,1"), row -> {})).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("헤더 순서에 의존하지 않고 연도 접미사를 제거한 history와 앞자리 0을 보존한다")
    void 헤더순서와이력키정규화_읽기_성공() {
        // given
        String csv = csv(Map.of("PANEL_ID", "0001", "KECO_CODE_2024", "0262",
                "SPEC_HISTORY_2024", "{\"ENGLISH_TEST_TYPE_2024\":[\"TOEIC\"],\"ENGLISH_TEST_SCORE_2024\":[875]}"));
        List<KeepSalaryPanelRecord> panels = new ArrayList<>();
        // when
        reader.forEachRecord(new StringReader(csv), panels::add);
        // then
        assertThat(panels.getFirst().panelId()).isEqualTo("0001");
        assertThat(panels.getFirst().years().getLast().values().get("KECO_CODE")).isEqualTo("0262");
        assertThat(panels.getFirst().years().getLast().history()).containsEntry("ENGLISH_TEST_SCORE", List.of("875"));
    }

    @Test
    @DisplayName("다른 연도 이력과 잘못된 JSON 값 및 중복 패널을 거절한다")
    void 손상이력과중복패널_읽기_실패() {
        // given / when / then
        for (String history : List.of("{\"KECO_CODE_2024\":[\"1332\"]}", "{\"KECO_CODE_2023\":{}}", "null", "{")) {
            String csv = csv(Map.of("PANEL_ID", "1", "SPEC_HISTORY_2023", history));
            assertThatThrownBy(() -> reader.forEachRecord(new StringReader(csv), panel -> {})).isInstanceOf(CustomException.class);
        }
        String valid = csv(Map.of("PANEL_ID", "1"));
        String repeated = valid + valid.substring(valid.indexOf('\n') + 1);
        assertThatThrownBy(() -> reader.forEachRecord(new StringReader(repeated), panel -> {})).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("실제 KEEP 파일의 모든 패널과 연도별 history를 스트리밍으로 검증한다")
    void 실제배포CSV_전체읽기_성공() throws IOException {
        // given
        AtomicInteger count = new AtomicInteger();
        // when
        try (Reader input = new InputStreamReader(new ClassPathResource("data/KEEP2_YEARLY_SPEC_SALARY.csv").getInputStream(), StandardCharsets.UTF_8)) {
            reader.forEachRecord(input, panel -> {
                assertThat(panel.years()).extracting(KeepSalaryPanelRecord.YearData::year).containsExactlyElementsOf(KeepSalaryCsvReader.YEARS);
                count.incrementAndGet();
            });
        }
        // then
        assertThat(count.get()).isEqualTo(10558);
    }

    private String csv(Map<String, String> values) {
        List<String> header = new ArrayList<>(List.of("PANEL_ID"));
        for (int year : KeepSalaryCsvReader.YEARS) {
            for (String field : KeepSalaryCsvReader.FIELDS) header.add(field + "_" + year);
            header.add("SPEC_HISTORY_" + year);
        }
        Collections.reverse(header);
        List<String> cells = header.stream().map(key -> "\"" + values.getOrDefault(key, "").replace("\"", "\"\"") + "\"").toList();
        return String.join(",", header) + "\n" + String.join(",", cells) + "\n";
    }
}
