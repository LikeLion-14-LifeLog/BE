package com.lifelog.domain.specprofile.service.openApi;

import com.lifelog.domain.specprofile.dto.KeepSalaryAccumulatedSpec;
import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSample;
import com.lifelog.domain.specprofile.dto.KeepSalaryMatchedSampleStatistics;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationSnapshot;
import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationAiResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnnualSalaryAnalyzationResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileTargetScenarioDto;
import com.lifelog.domain.specprofile.dto.SpecProfileTargetReferenceResDto;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.enums.CompanyGroupScale;
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
import tools.jackson.databind.node.ArrayNode;
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
    private final String simulationInstructions;
    private final JsonNode schema;
    private final JsonNode simulationSchema;
    private final String targetRecommendationInstructions;
    private final String targetEvaluationInstructions;
    private final JsonNode targetRecommendationSchema;
    private final JsonNode targetEvaluationSchema;

    public OpenApiPromptBuilder(JsonMapper jsonMapper, @Value("${openai.max-input-chars:200000}") int maxInputChars) {
        this.jsonMapper = jsonMapper;
        this.maxInputChars = maxInputChars;
        try (InputStream promptInput = new ClassPathResource("prompts/annual-salary-analyzation.v1.txt").getInputStream();
             InputStream simulationInput = new ClassPathResource("prompts/annual-salary-simulation.v1.txt").getInputStream();
             InputStream schemaInput = new ClassPathResource("prompts/annual-salary-analyzation.schema.json").getInputStream();
             InputStream targetInput = new ClassPathResource("prompts/target-spec-recommendation.v1.txt").getInputStream();
             InputStream evaluationInput = new ClassPathResource("prompts/target-spec-evaluation.v1.txt").getInputStream();
             InputStream targetSchemaInput = new ClassPathResource("prompts/target-spec-recommendation.schema.json").getInputStream();
             InputStream evaluationSchemaInput = new ClassPathResource("prompts/target-spec-evaluation.schema.json").getInputStream()) {
            this.instructions = new String(promptInput.readAllBytes(), StandardCharsets.UTF_8);
            this.simulationInstructions = new String(simulationInput.readAllBytes(), StandardCharsets.UTF_8);
            this.schema = jsonMapper.readTree(schemaInput.readAllBytes());
            this.targetRecommendationInstructions = new String(targetInput.readAllBytes(), StandardCharsets.UTF_8);
            this.targetEvaluationInstructions = new String(evaluationInput.readAllBytes(), StandardCharsets.UTF_8);
            this.targetRecommendationSchema = jsonMapper.readTree(targetSchemaInput.readAllBytes());
            this.targetEvaluationSchema = jsonMapper.readTree(evaluationSchemaInput.readAllBytes());
            this.simulationSchema = schema.deepCopy();
            ObjectNode properties = (ObjectNode) simulationSchema.path("properties");
            ArrayNode required = (ArrayNode) simulationSchema.path("required");
            for (String field : List.of("preparationPeriod", "estimatedCost", "decisionCriteria")) {
                properties.putObject(field).put("type", "string").put("minLength", 1)
                        .put("pattern", SpecProfileSimulationAiResDto.SINGLE_LINE_PATTERN);
                required.add(field);
            }
            ((ObjectNode) properties.path("preparationPeriod"))
                    .put("maxLength", SpecProfileSimulationAiResDto.PREPARATION_PERIOD_MAX_LENGTH)
                    .put("description", "준비 기간만 짧게 표기해요. 예: 약 2~3개월. 산정 가정은 쓰지 않아요.");
            ((ObjectNode) properties.path("estimatedCost"))
                    .put("maxLength", SpecProfileSimulationAiResDto.ESTIMATED_COST_MAX_LENGTH)
                    .put("description", "원화 금액·범위 또는 비용 상태만 표기해요. 예: 약 0~15만 원. 산정 근거·가정·제외 항목은 쓰지 않아요.");
            ((ObjectNode) properties.path("decisionCriteria"))
                    .put("maxLength", SpecProfileSimulationAiResDto.DECISION_CRITERIA_MAX_LENGTH)
                    .put("pattern", SpecProfileSimulationAiResDto.POLITE_SENTENCE_PATTERN)
                    .put("description", "직무 활용도만 해요체 한 문장으로 써요. 예: 실무 경험을 중시하는 채용에 적합해요.");
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
        OpenApiPrompt result = buildPrompt(snapshot, conditions, samples, statistics, null, false, null, null);
        log.info("[OpenApiPromptBuilder] 연봉 프롬프트 생성 | buildAnnualSalaryAnalyzationPrompt() - END");
        return result;
    }

    /**
     * 기존 분석 입력에 기업 규모 조건과 일회성 목표 예측 지침을 더한다.
     * @param snapshot 현재 또는 목표 스냅샷
     * @param conditions 구간 계산을 마친 분류 조건
     * @param samples 실제 비교 표본
     * @param statistics 서버가 계산한 관측 통계
     * @param companyGroupScale 기업 규모. null이면 규모를 지정하지 않음
     * @return 연봉 예측과 준비 정보를 반환하는 시뮬레이션 전용 프롬프트
     */
    public OpenApiPrompt buildAnnualSalarySimulationPrompt(SpecProfileAnalyzationSnapshot snapshot,
            SpecProfileSalaryClassificationData conditions, List<KeepSalaryMatchedSample> samples,
            KeepSalaryMatchedSampleStatistics statistics, CompanyGroupScale companyGroupScale) {
        log.info("[OpenApiPromptBuilder] 시뮬레이션 프롬프트 생성 | buildAnnualSalarySimulationPrompt() - START");
        OpenApiPrompt result = buildPrompt(snapshot, conditions, samples, statistics, companyGroupScale, true, snapshot, null);
        log.info("[OpenApiPromptBuilder] 시뮬레이션 프롬프트 생성 | buildAnnualSalarySimulationPrompt() - END");
        return result;
    }

    /**
     * 현재 스펙을 함께 전달하여 목표 조건의 추가 준비 기간·비용·활용도를 추정한다.
     * @param snapshot 분석할 현재 또는 목표 스냅샷
     * @param conditions 구간 계산을 마친 분류 조건
     * @param samples 실제 비교 표본
     * @param statistics 서버 계산 통계
     * @param companyGroupScale 예측 기업 규모
     * @param baseSnapshot 추가 목표를 구분할 기준 현재 스펙
     * @return 기존 연봉 입력과 목표 준비 정보를 포함한 전용 프롬프트
     */
    public OpenApiPrompt buildAnnualSalarySimulationPrompt(SpecProfileAnalyzationSnapshot snapshot,
            SpecProfileSalaryClassificationData conditions, List<KeepSalaryMatchedSample> samples,
            KeepSalaryMatchedSampleStatistics statistics, CompanyGroupScale companyGroupScale,
            SpecProfileAnalyzationSnapshot baseSnapshot) {
        log.info("[OpenApiPromptBuilder] 목표 준비 정보 프롬프트 생성 | buildAnnualSalarySimulationPrompt() - START");
        OpenApiPrompt result = buildPrompt(snapshot, conditions, samples, statistics, companyGroupScale, true, baseSnapshot, null);
        log.info("[OpenApiPromptBuilder] 목표 준비 정보 프롬프트 생성 | buildAnnualSalarySimulationPrompt() - END");
        return result;
    }

    /**
     * 저장된 현재 분석의 초봉·3년차·5년차를 비교 기준으로 목표 예측 입력에 포함한다.
     * @param snapshot 목표 스냅샷
     * @param conditions 구간 계산을 마친 분류 조건
     * @param samples 실제 비교 표본
     * @param statistics 서버 계산 통계
     * @param companyGroupScale 목표 예측 기업 규모
     * @param baseSnapshot 기준 현재 스펙
     * @param baseAnalysis 현재 스펙으로 저장된 완료 분석
     * @return 기준 분석과 목표 조건을 함께 포함한 전용 프롬프트
     */
    public OpenApiPrompt buildAnnualSalarySimulationPrompt(SpecProfileAnalyzationSnapshot snapshot,
            SpecProfileSalaryClassificationData conditions, List<KeepSalaryMatchedSample> samples,
            KeepSalaryMatchedSampleStatistics statistics, CompanyGroupScale companyGroupScale,
            SpecProfileAnalyzationSnapshot baseSnapshot, SpecProfileAnnualSalaryAnalyzationResDto baseAnalysis) {
        log.info("[OpenApiPromptBuilder] 기준 분석 비교 프롬프트 생성 | buildAnnualSalarySimulationPrompt() - START");
        OpenApiPrompt result = buildPrompt(snapshot, conditions, samples, statistics, companyGroupScale, true, baseSnapshot, baseAnalysis);
        log.info("[OpenApiPromptBuilder] 기준 분석 비교 프롬프트 생성 | buildAnnualSalarySimulationPrompt() - END");
        return result;
    }

    /**
     * CURRENT·사용자 목표의 확정 분석과 실제 참조 목록으로 추가 스펙 대안 생성을 요청한다.
     * @param base 요청 CURRENT
     * @param baseAnalysis 비교 기준인 저장된 현재 분석
     * @param storedAnalysis 동일 CURRENT의 기존 저장 진단. 없으면 null
     * @param user 사용자 목표와 준비 정보
     * @param scale 공통 기업 규모
     * @param references 사용 가능한 실제 마스터 ID
     * @param feedback 이전 생성의 검증 실패 이유
     * @return 대안 두 개를 생성할 프롬프트
     */
    public OpenApiPrompt buildTargetSpecRecommendationPrompt(SpecProfileAnalyzationSnapshot base,
            SpecProfileAnnualSalaryAnalyzationResDto baseAnalysis, SpecProfileAnnualSalaryAnalyzationResDto storedAnalysis,
            SpecProfileTargetScenarioDto user, CompanyGroupScale scale, SpecProfileTargetReferenceResDto references, String feedback) {
        log.info("[OpenApiPromptBuilder] 목표 대안 프롬프트 생성 | buildTargetSpecRecommendationPrompt() - START");
        ObjectNode input = jsonMapper.createObjectNode();
        input.set("currentSpec", toPromptSnapshot(base));
        input.set("currentSimulationAnalysis", toPromptReport(baseAnalysis));
        if (storedAnalysis == null) input.putNull("currentStoredAnalysis");
        else input.set("currentStoredAnalysis", toPromptReport(storedAnalysis));
        input.set("userTarget", toPromptScenario(user));
        input.set("allowedReferences", jsonMapper.valueToTree(references));
        if (scale == null) input.putNull("companyGroupScale");
        else input.put("companyGroupScale", scale.name());
        if (feedback == null) input.putNull("validationFeedback");
        else input.put("validationFeedback", feedback);
        OpenApiPrompt result = buildTargetPrompt(targetRecommendationInstructions, input,
                "target_spec_recommendation_v1", targetRecommendationSchema);
        log.info("[OpenApiPromptBuilder] 목표 대안 프롬프트 생성 | buildTargetSpecRecommendationPrompt() - END");
        return result;
    }

    /**
     * 재분석을 마친 값만 사용해 사용자 목표 대비 두 대안의 종합 우수성을 평가한다.
     * @param base 요청 CURRENT
     * @param baseAnalysis 비교 기준인 저장된 현재 분석
     * @param user 사용자 목표
     * @param alternatives 슬롯 2·3의 확정 결과
     * @param scale 공통 기업 규모
     * @return 수치를 수정하지 않는 종합 평가 프롬프트
     */
    public OpenApiPrompt buildTargetSpecEvaluationPrompt(SpecProfileAnalyzationSnapshot base,
            SpecProfileAnnualSalaryAnalyzationResDto baseAnalysis, SpecProfileTargetScenarioDto user,
            List<SpecProfileTargetScenarioDto> alternatives, CompanyGroupScale scale) {
        log.info("[OpenApiPromptBuilder] 대안 종합 평가 생성 | buildTargetSpecEvaluationPrompt() - START");
        ObjectNode input = jsonMapper.createObjectNode();
        input.set("currentSpec", toPromptSnapshot(base));
        input.set("currentSimulationAnalysis", toPromptReport(baseAnalysis));
        input.set("userTarget", toPromptScenario(user));
        ArrayNode options = input.putArray("alternatives");
        for (int index = 0; index < alternatives.size(); index++) {
            ObjectNode option = toPromptScenario(alternatives.get(index));
            option.put("slotNumber", index + 2);
            options.add(option);
        }
        if (scale == null) input.putNull("companyGroupScale");
        else input.put("companyGroupScale", scale.name());
        OpenApiPrompt result = buildTargetPrompt(targetEvaluationInstructions, input, "target_spec_evaluation_v1", targetEvaluationSchema);
        log.info("[OpenApiPromptBuilder] 대안 종합 평가 생성 | buildTargetSpecEvaluationPrompt() - END");
        return result;
    }

    /** 별도로 전달한 상세 스펙과 중복되는 적용 스펙의 사용자 항목 PK를 보고서에서 제거한다. */
    private ObjectNode toPromptReport(SpecProfileAnnualSalaryAnalyzationResDto report) {
        ObjectNode result = jsonMapper.valueToTree(report);
        result.remove("appliedSpecs");
        return result;
    }

    /** 마스터 참조만 있는 changes와 식별자를 제거한 스냅샷·분석을 조합한다. */
    private ObjectNode toPromptScenario(SpecProfileTargetScenarioDto scenario) {
        ObjectNode result = jsonMapper.createObjectNode();
        result.put("title", scenario.title());
        result.set("changes", jsonMapper.valueToTree(scenario.changes()));
        result.set("spec", toPromptSnapshot(scenario.snapshot()));
        result.set("analysis", toPromptReport(scenario.analysis().analysis()));
        result.put("preparationPeriod", scenario.analysis().preparationPeriod());
        result.put("estimatedCost", scenario.analysis().estimatedCost());
        result.put("decisionCriteria", scenario.analysis().decisionCriteria());
        return result;
    }

    /** 추천과 평가에도 동일한 입력 크기 제한과 엄격한 JSON Schema를 적용한다. */
    private OpenApiPrompt buildTargetPrompt(String developerInstructions, ObjectNode input, String schemaName, JsonNode outputSchema) {
        try {
            String json = jsonMapper.writeValueAsString(input);
            if (json.length() + developerInstructions.length() > maxInputChars) {
                throw new CustomException(OpenApiErrorCode.OPEN_API_INPUT_TOO_LARGE);
            }
            return new OpenApiPrompt(developerInstructions, json, schemaName, outputSchema.deepCopy());
        } catch (JacksonException exception) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.ANNUAL_SALARY_PROMPT_BUILD_FAILED);
        }
    }

    /** 식별자 제거·표본 압축·입력 한도 검증을 두 프롬프트 생성 경로에서 공유한다. */
    private OpenApiPrompt buildPrompt(SpecProfileAnalyzationSnapshot snapshot,
            SpecProfileSalaryClassificationData conditions, List<KeepSalaryMatchedSample> samples,
            KeepSalaryMatchedSampleStatistics statistics, CompanyGroupScale companyGroupScale, boolean simulation,
            SpecProfileAnalyzationSnapshot baseSnapshot, SpecProfileAnnualSalaryAnalyzationResDto baseAnalysis) {
        String developerInstructions = simulation ? instructions + "\n\n" + simulationInstructions : instructions;
        try {
            /* (1) DB 식별자를 제거하고 Enum 이름과 스냅샷의 상세 스펙을 보존한다. */
            ObjectNode snapshotData = toPromptSnapshot(snapshot);
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
            ObjectNode input = jsonMapper.valueToTree(new PromptInput(snapshotData, classificationData,
                    matchingPolicy(), statistics, groups, List.of(0, 3, 5)));
            if (simulation) {
                if (companyGroupScale == null) input.putNull("companyGroupScale");
                else input.put("companyGroupScale", companyGroupScale.name());
                input.set("baseSnapshot", toPromptSnapshot(baseSnapshot == null ? snapshot : baseSnapshot));
                if (baseAnalysis != null) input.set("baseAnalysis", toPromptReport(baseAnalysis));
            }
            String inputJson = jsonMapper.writeValueAsString(input);
            /* (3) 한도를 넘으면 일부 표본을 임의로 생략하지 않고 명시적으로 실패한다. */
            if (inputJson.length() + developerInstructions.length() > maxInputChars) {
                throw new CustomException(OpenApiErrorCode.OPEN_API_INPUT_TOO_LARGE);
            }
            log.debug("[OpenApiPromptBuilder] 공통 입력 생성 | samples: {}, groups: {}, chars: {}",
                    samples.size(), groups.size(), inputJson.length());
            return new OpenApiPrompt(developerInstructions, inputJson,
                    simulation ? "annual_salary_simulation_v1" : "annual_salary_analyzation_v1",
                    (simulation ? simulationSchema : schema).deepCopy());
        } catch (JacksonException exception) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.ANNUAL_SALARY_PROMPT_BUILD_FAILED);
        }
    }

    /** 현재·목표 스냅샷에서 DB 식별자를 제거하고 상세 스펙을 보존한다. */
    private ObjectNode toPromptSnapshot(SpecProfileAnalyzationSnapshot snapshot) {
        ObjectNode result = jsonMapper.valueToTree(snapshot);
        result.remove("specProfileId");
        result.remove("jobId");
        removeIds(result, "academicAbilities", List.of("academicAbilityId", "majorId"));
        removeIds(result, "certificates", List.of("specProfileCertificateId", "certificateId"));
        removeIds(result, "languageExamScores", List.of("languageExamScoreId", "languageExamId"));
        removeIds(result, "experiences", List.of("experienceId"));
        return result;
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
