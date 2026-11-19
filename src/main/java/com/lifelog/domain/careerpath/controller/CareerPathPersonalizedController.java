
package com.lifelog.domain.careerpath.controller;

import com.lifelog.domain.careerpath.service.CareerPathPersonalizedService;
import com.lifelog.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(
        name = "개인화 커리어 경로",
        description = "현재 회원의 전공 계열 기반 커리어 경로 분석 API"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/career-paths/yearly/personalized")
public class CareerPathPersonalizedController {

    private final CareerPathPersonalizedService service;

    @Operation(summary = "개인화 커리어 경로 목록 조회")
    @GetMapping("/routes")
    public ApiResponse<Map<String, Object>> getRoutes(

            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @RequestParam(required = false)
            String fromCode
    ) {

        return ApiResponse.success(
                service.getRoutes(memberId, fromCode)
        );
    }

    @Operation(summary = "개인화 커리어 경로 그래프 조회")
    @GetMapping("/graph")
    public ApiResponse<Map<String, Object>> getGraph(

            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @RequestParam(required = false)
            String fromCode
    ) {

        return ApiResponse.success(
                service.getGraph(memberId, fromCode)
        );
    }

    @Operation(summary = "개인화 커리어 경로 상세 분석")
    @GetMapping("/routes/{source}/{target}/analysis")
    public ApiResponse<Map<String, Object>> getAnalysis(

            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @PathVariable String source,

            @PathVariable String target
    ) {

        return ApiResponse.success(
                service.getAnalysis(
                        memberId,
                        source,
                        target
                )
        );
    }
}
