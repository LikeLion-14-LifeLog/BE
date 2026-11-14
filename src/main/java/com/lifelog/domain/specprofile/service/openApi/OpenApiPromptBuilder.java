package com.lifelog.domain.specprofile.service.openApi;

import com.lifelog.domain.specprofile.dto.KeepSalaryAccumulatedSpec;
import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSample;
import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSampleStatistics;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationSnapshot;
import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.infra.openai.OpenApiErrorCode;
import com.lifelog.global.infra.openai.OpenApiPrompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OpenApiPromptBuilder {
    private final JsonMapper jsonMapper;
    private final int maxInputChars;
    private final String instructions;
    private final JsonNode schema;

    public OpenApiPromptBuilder(JsonMapper jsonMapper, @Value("${openai.max-input-chars:200000}") int maxInputChars) {
        this.jsonMapper = jsonMapper;
        this.maxInputChars = maxInputChars;
        try (InputStream promptInput = new ClassPathResource("prompts/annual-salary-analyzation.v1.txt").getInputStream();
             InputStream schemaInput = new ClassPathResource("prompts/annual-salary-analyzation.schema.json").getInputStream()) {
            this.instructions = new String(promptInput.readAllBytes(), StandardCharsets.UTF_8);
            this.schema = jsonMapper.readTree(schemaInput.readAllBytes());
        } catch (IOException | JacksonException exception) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.ANNUAL_SALARY_PROMPT_BUILD_FAILED);
        }
    }

    /**
     * 스냅샷의 상세 스펙, 실제 분류 조건, 전체 표본, 서버 통계로 연봉 분석 입력을 만든다.
     * @param snapshot 요청 시점 전체 스펙
     * @param conditions 점수 구간 계산을 마친 분류 조건
     * @param samples 패널당 하나의 연도를 선택한 표본
     * @param statistics 서버 계산 통계
     * @return 식별자를 제외하고 동일 표본만 손실 없이 묶은 프롬프트
     */
    public OpenApiPrompt buildAnnualSalaryAnalyzationPrompt(SpecProfileAnalyzationSnapshot snapshot,
            SpecProfileSalaryClassificationData conditions, List<KeepSalaryMatchedSample> samples,
            KeepSalaryMatchedSampleStatistics statistics) {
        log.info("[OpenApiPromptBuilder] 연봉 프롬프트 생성 | buildAnnualSalaryAnalyzationPrompt() - START");
        try {
            /* (1) DB 식별자를 제거하고 Enum 이름과 스냅샷의 상세 스펙을 보존한다. */
            ObjectNode snapshotData = jsonMapper.valueToTree(snapshot);
            snapshotData.remove("specProfileId");
            snapshotData.remove("jobId");
            removeIds(snapshotData, "academicAbilities", List.of("academicAbilityId", "majorId"));
            removeIds(snapshotData, "certificates", List.of("specProfileCertificateId", "certificateId"));
            removeIds(snapshotData, "languageExamScores", List.of("languageExamScoreId", "languageExamId"));
            removeIds(snapshotData, "experiences", List.of("experienceId"));
            ObjectNode classificationData = jsonMapper.valueToTree(conditions);
            removeIds(classificationData, "languageExamScores", List.of("languageExamId"));
            /* (2) 사람 ID를 제외한 모든 값이 동일할 때만 빈도로 묶는다. 표본을 잘라내지 않는다. */
            Map<SampleData, Integer> frequencies = new LinkedHashMap<>();
            for (KeepSalaryMatchedSample sample : samples) {
                SampleData data = new SampleData(sample.selectedYear(), sample.annualSalaryKrw(),
                        sample.accumulatedSpec(), sample.similarityScore(), sample.directMatchCount(), sample.matchedConditions());
                frequencies.merge(data, 1, Integer::sum);
            }
            List<SampleGroup> groups = frequencies.entrySet().stream()
                    .map(entry -> new SampleGroup(entry.getKey(), entry.getValue())).toList();
            String inputJson = jsonMapper.writeValueAsString(new PromptInput(snapshotData, classificationData,
                    matchingPolicy(), statistics, groups, List.of(0, 3, 5)));
            /* (3) 한도를 넘으면 일부 표본을 임의로 생략하지 않고 명시적으로 실패한다. */
            if (inputJson.length() + instructions.length() > maxInputChars) {
                throw new CustomException(OpenApiErrorCode.OPEN_API_INPUT_TOO_LARGE);
            }
            log.info("[OpenApiPromptBuilder] 연봉 프롬프트 생성 | buildAnnualSalaryAnalyzationPrompt() - END | samples: {}, groups: {}, chars: {}",
                    samples.size(), groups.size(), inputJson.length());
            return new OpenApiPrompt(instructions, inputJson, "annual_salary_analyzation_v1", schema.deepCopy());
        } catch (JacksonException exception) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.ANNUAL_SALARY_PROMPT_BUILD_FAILED);
        }
    }

    /** 필수 필터와 유사도 평가를 구분해 AI가 점수 조건까지 모두 일치했다고 해석하지 않도록 한다. */
    private MatchingPolicy matchingPolicy() {
        return new MatchingPolicy(
                List.of("JOB_SUB_CATEGORY", "ACADEMIC_TYPE", "ACADEMIC_FIELD", "ACADEMIC_MAJOR_MIDDLE_FIELD", "ACADEMIC_LOCATION"),
                List.of("GPA_SAME_SCALE_BAND", "SAME_ENGLISH_TEST_SCORE_BAND_OR_GRADE", "CERTIFICATE_COUNT"),
                List.of("ACADEMIC_STATUS", "GRADUATED_YEAR_MONTH"),
                "입력한 학교별 조건을 모두 충족해야 하며, 전공은 같은 학위·계열·중분류의 전공 코드로 비교한다. UNIVERSITY는 4·5·6년제를 포함한다.",
                "학점·영어 숫자 구간은 100/(1+구간 거리), 자격증 수는 100/(1+개수 차이)의 정수 부분을 합산한다. 동일 시험 등급은 같을 때만 100점이다. 다른 만점·시험 종류, 누락·모호한 관측값은 0점이며 제외 사유가 아니다.",
                "각 사람의 해당 시점까지 누적된 값으로 비교해 유사도 점수, 해당 연도 직접 일치 수, 최신 연도 순으로 연도 하나를 선택한다. 필수 조건 통과자는 유사도 최저점 없이 모두 포함한다.",
                "통계는 선택된 사람마다 동일 비중으로 계산한다. 유사도는 연도 선택용이며 연봉 가산점이나 통계 가중치가 아니다.");
    }

    /** 배열 내부 DTO의 DB 식별자만 제거한다. */
    private void removeIds(ObjectNode root, String field, List<String> ids) {
        for (JsonNode item : root.path(field)) {
            if (item instanceof ObjectNode object) {
                for (String id : ids) object.remove(id);
            }
        }
    }

    private record PromptInput(ObjectNode snapshot, ObjectNode classificationData, MatchingPolicy matchingPolicy,
                               KeepSalaryMatchedSampleStatistics statistics, List<SampleGroup> samples,
                               List<Integer> forecastAnnuals) {}
    private record SampleData(int selectedYear, BigDecimal annualSalaryKrw,
                              KeepSalaryAccumulatedSpec accumulatedSpec, int similarityScore, int directMatchCount,
                              List<KeepSalaryMatchedSample.MatchEvidence> matchedConditions) {}
    private record SampleGroup(SampleData data, int frequency) {}
    private record MatchingPolicy(List<String> mandatoryConditions, List<String> similarityConditions,
                                  List<String> nonFilteringConditions, String academicRule, String similarityScoreRule,
                                  String yearSelectionRule, String statisticsRule) {}
}
