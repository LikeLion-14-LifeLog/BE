package com.lifelog.domain.careerpath.controller;

import com.lifelog.domain.careerpath.service.CareerPathPersonalizedService;
import com.lifelog.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(
        name = "커리어 경로 연봉 분석",
        description = "전체 응답자 기반 커리어 경로 연봉 분석 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/career-paths/yearly")
public class CareerPathSalaryAnalyticsController {

    private final CareerPathPersonalizedService service;

    @Operation(
            summary = "전체 커리어 경로 연봉 분석",
            description = "전공 계열 필터 없이 출발 및 도착 직업코드에 해당하는 "
                    + "전체 응답자의 평균, 중앙값, P75 연봉을 조회합니다."
    )
    @GetMapping("/routes/{source}/{target}/salary-analysis")
    public ApiResponse<Map<String, Object>> getOverallSalaryAnalysis(

            @Parameter(description = "출발 직업코드", example = "1332")
            @PathVariable String source,

            @Parameter(description = "도착 직업코드", example = "1331")
            @PathVariable String target
    ) {

        return ApiResponse.success(
                service.getOverallSalaryAnalysis(source, target)
        );
    }
}