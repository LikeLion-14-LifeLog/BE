package com.lifelog.domain.specprofile;

import com.lifelog.domain.major.service.MajorCsvReader;
import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.dto.KeepSalaryPanelRecord.YearData;
import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData.AcademicCondition;
import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData.LanguageCondition;
import com.lifelog.domain.specprofile.enums.AcademicAbilityType;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.mapper.KeepSalaryCodeMapper;
import com.lifelog.domain.specprofile.service.*;
import com.lifelog.domain.specprofile.service.keep.KeepSalaryConditionMatcher;
import com.lifelog.domain.specprofile.service.keep.KeepSalaryCsvReader;
import com.lifelog.domain.specprofile.service.keep.KeepSalaryRecordSelectionService;
import com.lifelog.domain.specprofile.service.keep.KeepSalarySpecAccumulator;
import com.lifelog.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.*;

class KeepSalaryRecordSelectionServiceTest {
    @TempDir Path directory;
    private final KeepSalaryCsvReader reader = new KeepSalaryCsvReader(JsonMapper.builder().build());
    private final DefaultResourceLoader resources = new DefaultResourceLoader();
    private final KeepSalaryCodeMapper mapper = new KeepSalaryCodeMapper(new MajorCsvReader());
    private final SpecProfileSalaryClassificationData conditions = new SpecProfileSalaryClassificationData("133", "개발", List.of(), List.of(), 2);

    @Test
    @DisplayName("패널마다 조건을 만족하는 연도 하나만 선택하며 동점이면 최신 연도를 사용한다")
    void 최신동점연도와고유표본_선택_성공() {
        // given / when
        List<KeepSalaryMatchedSample> result = service("classpath:data/keep_salary_preparation.csv").findBestSamples(conditions);
        // then
        assertThat(result).hasSize(10).allSatisfy(sample -> {
            assertThat(sample.selectedYear()).isEqualTo(2024);
            assertThat(sample.annualSalaryKrw()).isEqualByComparingTo("40000000");
            assertThat(sample.accumulatedSpec().fields()).doesNotContainKey("ANNUAL_SALARY_KRW");
        });
        assertThat(result).extracting(KeepSalaryMatchedSample::panelId).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("이후 연도가 과거 조건으로 통과해도 직접 일치 수가 높은 과거 연도를 선택한다")
    void 과거직접일치우선과미래값차단_선택_성공() throws IOException {
        // given
        String path = fixture(10, panel -> change(panel, row -> {
            if (row.year() != 2024) return row;
            Map<String, String> values = new HashMap<>(row.values());
            values.put("KECO_CODE", "6110");
            values.put("CERTIFICATE_TOTAL_COUNT", "99");
            values.put("INTERNSHIP_COUNT", "5");
            values.put("ANNUAL_SALARY_KRW", "999999999");
            return new YearData(row.year(), values, row.history());
        }));
        // when
        List<KeepSalaryMatchedSample> result = service(path).findBestSamples(conditions);
        // then
        assertThat(result).allSatisfy(sample -> {
            assertThat(sample.selectedYear()).isEqualTo(2021);
            assertThat(sample.annualSalaryKrw()).isEqualByComparingTo("40000000");
            assertThat(sample.accumulatedSpec().fields().get("INTERNSHIP_COUNT")).isEmpty();
            assertThat(sample.accumulatedSpec().fields().get("CERTIFICATE_TOTAL_COUNT")).containsExactly("2");
        });
    }

    @Test
    @DisplayName("9개 패널의 여러 연도를 중복 표본으로 세거나 불일치 행으로 10개를 채우지 않는다")
    void 최소표본미달_선택_실패() throws IOException {
        // given
        String path = fixture(10, panel -> panel.panelId().equals("fixture-9") ? change(panel, row -> {
            Map<String, String> values = new HashMap<>(row.values());
            values.put("KECO_CODE", "6110");
            return new YearData(row.year(), values, row.history());
        }) : panel);
        // when / then
        assertThatThrownBy(() -> service(path).findBestSamples(conditions)).isInstanceOfSatisfying(CustomException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(SpecProfileAnalyzationErrorCode.INSUFFICIENT_MATCHED_SAMPLES);
            assertThat(error.getData()).isEqualTo(Map.of("matchedSampleCount", 9));
        });
    }

    @Test
    @DisplayName("연봉 없는 마지막 연도의 직무를 앞선 연도의 조건 충족 근거로 사용하지 않는다")
    void 미래직무소급과무연봉후보_선택_실패() throws IOException {
        // given
        String path = fixture(10, panel -> change(panel, row -> {
            Map<String, String> values = new HashMap<>(row.values());
            values.put("KECO_CODE", row.year() == 2024 ? "1332" : "6110");
            if (row.year() == 2024) values.put("ANNUAL_SALARY_KRW", "");
            return new YearData(row.year(), values, row.history());
        }));
        // when / then
        assertThatThrownBy(() -> service(path).findBestSamples(conditions)).isInstanceOfSatisfying(CustomException.class,
                error -> assertThat(error.getData()).isEqualTo(Map.of("matchedSampleCount", 0)));
    }

    @Test
    @DisplayName("기존 2025 직무 사전으로 소분류를 조회하고 미분류와 없는 코드는 매칭하지 않는다")
    void 변환완료직업코드_소분류조회_성공() {
        // given / when
        KeepSalaryCodeMapper.Codebook result = mapper.loadCodebook();
        // then
        assertThat(result.jobCategories()).containsEntry("1332", "133").containsEntry("133", "133")
                .containsEntry("0262", "026").doesNotContainKeys("9999", "9998");
    }

    @Test
    @DisplayName("유사도가 높은 연도를 직접 일치 수보다 우선하고 과거 직무와 자격증을 누적한다")
    void 유사도우선과누적비교_선택_성공() throws IOException {
        // given
        String path = fixture(10, panel -> change(panel, row -> {
            Map<String, String> values = new HashMap<>(row.values());
            values.put("ENGLISH_TEST_TYPE", "");
            values.put("ENGLISH_TEST_SCORE", "");
            if (row.year() == 2024) {
                values.put("KECO_CODE", "6110");
                values.put("CERTIFICATE_TOTAL_COUNT", "99");
                values.put("ENGLISH_TEST_TYPE", "TOEIC");
                values.put("ENGLISH_TEST_SCORE", "875");
            }
            return new YearData(row.year(), values, Map.of());
        }));
        SpecProfileSalaryClassificationData data = new SpecProfileSalaryScoreBandService().classify(
                new SpecProfileSalaryClassificationData("133", "개발", List.of(),
                        List.of(new LanguageCondition(1L, "TOEIC", new BigDecimal("865"), null, null, null)), 2));
        // when
        List<KeepSalaryMatchedSample> result = service(path).findBestSamples(data);
        // then
        assertThat(result).hasSize(10).allSatisfy(sample -> {
            assertThat(sample.selectedYear()).isEqualTo(2024);
            assertThat(sample.similarityScore()).isEqualTo(200);
            assertThat(sample.directMatchCount()).isEqualTo(1);
            assertThat(sample.accumulatedSpec().fields().get("KECO_CODE")).contains("1332", "6110");
        });
    }

    @Test
    @DisplayName("소프트웨어 전공 서울 대학교 GPA 3.86 TOEIC 865 자격증 2개 조건에서 실제 CSV 표본을 확보한다")
    void 사용자조건_실제CSV완화검색_성공() {
        // given
        SpecProfileSalaryClassificationData data = new SpecProfileSalaryScoreBandService().classify(
                new SpecProfileSalaryClassificationData("133", "개발", List.of(new AcademicCondition(AcademicAbilityType.UNIVERSITY,
                        ResidentialArea.SEOUL, MajorMainField.ENGINEERING, "소프트웨어학과", new BigDecimal("3.86"), new BigDecimal("4.5"), null)),
                        List.of(new LanguageCondition(1L, "TOEIC", new BigDecimal("865"), null, null, null)), 2));
        // when
        List<KeepSalaryMatchedSample> result = service("classpath:data/KEEP2_YEARLY_SPEC_SALARY.csv").findBestSamples(data);
        // then
        assertThat(result).hasSize(34);
        assertThat(result).extracting(KeepSalaryMatchedSample::panelId).doesNotHaveDuplicates();
        assertThat(result).allSatisfy(sample -> {
            assertThat(sample.annualSalaryKrw()).isPositive();
            assertThat(sample.similarityScore()).isBetween(0, 300);
        });
    }

    private KeepSalaryRecordSelectionService service(String path) {
        return new KeepSalaryRecordSelectionService(reader, new KeepSalarySpecAccumulator(), new KeepSalaryConditionMatcher(), mapper, resources, path);
    }

    private KeepSalaryPanelRecord change(KeepSalaryPanelRecord panel, UnaryOperator<YearData> transform) {
        return new KeepSalaryPanelRecord(panel.panelId(), panel.years().stream().map(transform).toList());
    }

    private String fixture(int count, UnaryOperator<KeepSalaryPanelRecord> transform) throws IOException {
        List<KeepSalaryPanelRecord> panels = new ArrayList<>();
        try (Reader input = new InputStreamReader(new ClassPathResource("data/keep_salary_preparation.csv").getInputStream(), StandardCharsets.UTF_8)) {
            reader.forEachRecord(input, panels::add);
        }
        List<String> header = new ArrayList<>(List.of("PANEL_ID"));
        for (int year : KeepSalaryCsvReader.YEARS) {
            for (String key : KeepSalaryCsvReader.FIELDS) header.add(key + "_" + year);
            header.add("SPEC_HISTORY_" + year);
        }
        Path path = directory.resolve("fixture.csv");
        try (BufferedWriter output = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            output.write(String.join(",", header));
            output.newLine();
            for (KeepSalaryPanelRecord original : panels.subList(0, count)) {
                KeepSalaryPanelRecord panel = transform.apply(original);
                List<String> values = new ArrayList<>(List.of(panel.panelId()));
                for (YearData row : panel.years()) {
                    for (String key : KeepSalaryCsvReader.FIELDS) values.add(row.values().getOrDefault(key, ""));
                    values.add("");
                }
                output.write(String.join(",", values));
                output.newLine();
            }
        }
        return path.toUri().toString();
    }
}
