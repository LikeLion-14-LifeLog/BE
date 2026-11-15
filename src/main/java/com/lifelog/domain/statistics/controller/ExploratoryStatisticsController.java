package com.lifelog.domain.statistics.controller;

import com.lifelog.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/** KEEP II 집계값만 제공. 개인 식별정보 및 사업장명은 포함하지 않음. */
@Tag(name = "Statistics", description = "KEEPⅡ 첫 이직 잠정 후보 탐색적 통계")
@RestController
@RequestMapping("/api/v1/statistics")
public class ExploratoryStatisticsController {

    private final JsonNode exploratoryStatistics;

    public ExploratoryStatisticsController(ObjectMapper objectMapper) throws IOException {
        try (var input = new ClassPathResource("data/lifelog-exploratory-statistics.json").getInputStream()) {
            this.exploratoryStatistics = objectMapper.readTree(input);
        }
    }

    @Operation(summary = "첫 이직 잠정 후보 탐색적 통계 조회", description = "2025년 CPI 환산 연환산 실수령 급여. 확정 첫 이직자 통계가 아닙니다. JWT 인증 필요.")
    @GetMapping("/exploratory-first-job-change")
    public ApiResponse<JsonNode> getExploratoryFirstJobChange() {
        return ApiResponse.success(exploratoryStatistics);
    }
}
