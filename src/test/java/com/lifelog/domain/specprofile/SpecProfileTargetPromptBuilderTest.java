package com.lifelog.domain.specprofile;

import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.enums.CompanyGroupScale;
import com.lifelog.domain.specprofile.service.openApi.OpenApiPromptBuilder;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.infra.openai.OpenApiErrorCode;
import com.lifelog.global.infra.openai.OpenApiPrompt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.RecordComponent;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecProfileTargetPromptBuilderTest {
    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
    private final SpecProfileAnalyzationSnapshot snapshot = SpecProfileAnalyzationSnapshot.builder()
            .specProfileId(123L).jobId(456L).jobName("개발자").birthYear(1999)
            .academicAbilities(List.of()).certificates(List.of(CertificateResDto.builder().specProfileCertificateId(789L)
                    .certificateId(55L).certificateName("기존 자격증").build()))
            .languageExamScores(List.of()).experiences(List.of()).build();
    private final SpecProfileAnnualSalaryAnalyzationResDto report = SpecProfileAnnualSalaryAnalyzationResDto.builder()
            .summary("CURRENT 분석").recommendation("직무 준비")
            .salaryResults(List.of(new SpecProfileAnnualSalaryAnalyzationResDto.SalaryResult(0, 34000000L, 36000000L, 38000000L, null)))
            .appliedSpecs(new SpecProfileAnnualSalaryAnalyzationResDto.AppliedSpecs(1999, null, 456L, "개발자",
                    List.of(), snapshot.certificates(), List.of(), List.of())).salaryNotice("관측 통계와 AI 예측 구분").build();
    private final SpecProfileTargetScenarioDto user = SpecProfileTargetScenarioDto.builder().title("사용자 목표")
            .changes(new SpecProfileTargetChanges(null, List.of(new SpecProfileTargetChanges.CertificateChange(77L, YearMonth.of(2027, 1))), null, null))
            .snapshot(snapshot).analysis(new SpecProfileSimulationAnalysis(report, "약 6개월", "약 35만 원", "직무 활용도")).build();
    private final SpecProfileTargetReferenceResDto references = new SpecProfileTargetReferenceResDto(List.of(),
            List.of(new SpecProfileTargetReferenceResDto.CertificateReference(77L, "목표 자격증")), List.of());

    @Test
    @DisplayName("추천에는 CURRENT·사용자 목표 분석과 준비 정보를 전달하며 사용자 DB PK는 제거한다")
    void 추천프롬프트_분석전달과식별자제거_성공() {
        // given / when
        OpenApiPrompt prompt = new OpenApiPromptBuilder(jsonMapper, 200000).buildTargetSpecRecommendationPrompt(
                snapshot, report, report, user, CompanyGroupScale.LARGE, references, "중복 준비안을 바꿔 주세요");
        JsonNode input = jsonMapper.readTree(prompt.inputJson());
        // then
        assertThat(prompt.schemaName()).isEqualTo("target_spec_recommendation_v1");
        assertThat(input.path("currentSpec").path("jobName").asString()).isEqualTo("개발자");
        assertThat(input.path("currentStoredAnalysis").path("summary").asString()).isEqualTo("CURRENT 분석");
        assertThat(input.path("currentSimulationAnalysis").path("salaryResults").get(0).path("annualIncomeExpected").asLong()).isEqualTo(36000000L);
        assertThat(input.path("userTarget").path("estimatedCost").asString()).isEqualTo("약 35만 원");
        assertThat(input.path("userTarget").path("changes").path("certificates").get(0).path("certificateId").asLong()).isEqualTo(77L);
        assertThat(input.path("allowedReferences").path("certificates").get(0).path("id").asLong()).isEqualTo(77L);
        assertThat(prompt.inputJson()).doesNotContain("specProfileId", "specProfileCertificateId", "jobId", "appliedSpecs");
        assertThat(prompt.developerInstructions()).contains("준비 기간", "비용", "직무 활용도", "실질적으로 달라야");
    }

    @Test
    @DisplayName("추천 JSON Schema의 필수 속성은 사용자 입력 DTO의 스펙 구조와 일치한다")
    void 추천스키마_입력형식일치_성공() {
        // given / when
        JsonNode schema = new OpenApiPromptBuilder(jsonMapper, 200000).buildTargetSpecRecommendationPrompt(
                snapshot, report, null, user, null, references, null).responseSchema();
        // then
        assertRecordSchema(schema.path("$defs").path("changes"), SpecProfileTargetChanges.class);
        assertRecordSchema(schema.path("$defs").path("academic"), SpecProfileTargetChanges.AcademicAbilityChange.class);
        assertRecordSchema(schema.path("$defs").path("certificate"), SpecProfileTargetChanges.CertificateChange.class);
        assertRecordSchema(schema.path("$defs").path("language"), SpecProfileTargetChanges.LanguageExamScoreChange.class);
        assertRecordSchema(schema.path("$defs").path("experience"), SpecProfileTargetChanges.ExperienceChange.class);
        assertThat(schema.path("properties").path("options").path("minItems").asInt()).isEqualTo(2);
        assertThat(schema.path("properties").path("options").path("maxItems").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("종합 평가는 최종 재분석 값과 슬롯을 전달하며 평가 결과만 생성한다")
    void 평가프롬프트_최종수치고정_성공() {
        // given / when
        OpenApiPrompt prompt = new OpenApiPromptBuilder(jsonMapper, 200000).buildTargetSpecEvaluationPrompt(
                snapshot, report, user, List.of(user, user), CompanyGroupScale.SMALL);
        JsonNode input = jsonMapper.readTree(prompt.inputJson());
        // then
        assertThat(prompt.schemaName()).isEqualTo("target_spec_evaluation_v1");
        assertThat(input.path("alternatives").get(0).path("slotNumber").asInt()).isEqualTo(2);
        assertThat(input.path("alternatives").get(1).path("slotNumber").asInt()).isEqualTo(3);
        assertThat(input.path("alternatives").get(0).path("analysis").path("salaryResults").get(0).path("annualIncomeExpected").asLong())
                .isEqualTo(36000000L);
        assertRecordSchema(prompt.responseSchema().path("properties").path("evaluations").path("items"),
                SpecProfileTargetEvaluationAiResDto.Evaluation.class);
        assertThat(prompt.developerInstructions()).contains("연봉이 낮아도", "수정하지 마라");
    }

    @Test
    @DisplayName("추천 프롬프트도 입력 한도를 초과하면 실패한다")
    void 추천입력한도초과_실패() {
        // given / when / then
        assertThatThrownBy(() -> new OpenApiPromptBuilder(jsonMapper, 1).buildTargetSpecRecommendationPrompt(
                snapshot, report, null, user, null, references, null)).isInstanceOfSatisfying(CustomException.class,
                value -> assertThat(value.getErrorCode()).isEqualTo(OpenApiErrorCode.OPEN_API_INPUT_TOO_LARGE));
    }

    private void assertRecordSchema(JsonNode schema, Class<?> recordType) {
        List<String> fields = Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toList();
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("properties").properties().stream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrderElementsOf(fields);
        assertThat(schema.path("required").valueStream().map(JsonNode::asString).toList()).containsExactlyInAnyOrderElementsOf(fields);
    }
}
