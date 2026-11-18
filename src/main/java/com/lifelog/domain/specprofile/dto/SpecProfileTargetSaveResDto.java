package com.lifelog.domain.specprofile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lifelog.domain.specprofile.enums.CompanyGroupScale;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SpecProfileTargetSaveResDto(
        @Schema(description = "기준 CURRENT ID", example = "10")
        Long baseSpecProfileId,
        @Schema(description = "세 선택지에 적용한 기업 규모", example = "LARGE", nullable = true)
        CompanyGroupScale companyGroupScale,
        @Schema(description = "금액 통화", example = "KRW")
        String currency,
        @Schema(description = "CURRENT의 첫 취업 예상 연봉(원)", example = "36000000")
        Long baseExpectedStartingSalary,
        @Schema(description = "저장한 사용자 TARGET ID", example = "11")
        Long selectedTargetSpecProfileId,
        @Schema(description = "슬롯 1은 사용자 목표, 슬롯 2·3은 저장하지 않는 AI 대안")
        List<Option> options,
        @Schema(description = "연봉 예측 및 비교 표본의 해석 안내")
        String salaryNotice,
        @Schema(description = "AI가 추정한 준비 정보의 해석 안내", example = "준비 기간·비용·활용도는 AI 참고 추정치로 개인 상황에 따라 달라질 수 있어요.")
        String preparationNotice
) {
    public enum Source { USER_INPUT, AI_RECOMMENDATION }

    @Builder
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Option(
            @Schema(description = "화면 슬롯 번호: 1, 2, 3", example = "1")
            int slotNumber,
            @Schema(description = "저장한 사용자 TARGET ID. 미저장 AI 대안은 null", example = "11", nullable = true)
            Long targetSpecProfileId,
            @Schema(description = "슬롯 1은 USER_INPUT, 슬롯 2·3은 AI_RECOMMENDATION", example = "USER_INPUT")
            Source source,
            @Schema(description = "사용자 슬롯 1만 true", example = "true")
            boolean selected,
            @Schema(description = "준비안 이름", example = "백엔드 개발 인턴")
            String title,
            @Schema(description = "해당 준비안의 추가 스펙. AI 대안을 선택하면 같은 PUT의 changes로 제출")
            SpecProfileTargetChanges changes,
            @Schema(description = "해당 목표의 첫 취업 예상 연봉(원)", example = "41000000")
            Long expectedStartingSalary,
            @Schema(description = "CURRENT 예상 초봉 대비 증감액(원). 음수 허용", example = "5000000")
            Long salaryIncreaseAmount,
            @Schema(description = "AI가 추정한 준비 기간만 한 줄로 표시", example = "약 6개월", maxLength = SpecProfileSimulationAiResDto.PREPARATION_PERIOD_MAX_LENGTH)
            String preparationPeriod,
            @Schema(description = "원화 비용·범위 또는 비용 상태만 표시. 산정 가정·제외 항목 미포함", example = "약 0~15만 원", maxLength = SpecProfileSimulationAiResDto.ESTIMATED_COST_MAX_LENGTH)
            String estimatedCost,
            @Schema(description = "직무 활용도만 해요체 한 문장으로 표시. AI 판단 과정 미포함", example = "실무 경험을 중시하는 채용에 적합해요.", maxLength = SpecProfileSimulationAiResDto.DECISION_CRITERIA_MAX_LENGTH)
            String decisionCriteria
    ) {}
}
