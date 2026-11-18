
package com.lifelog.domain.careerpath.controller;

import com.lifelog.domain.careerpath.service.CareerPathYearlyService;
import com.lifelog.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(
        name = "연도별 커리어 경로",
        description = "KEEPⅡ 데이터 기반 연도별 직업코드 이력 및 변화 그래프 조회 API"
)
@RestController
@RequestMapping("/api/v1/career-paths/yearly")
public class CareerPathYearlyController {

    private final CareerPathYearlyService service;

    public CareerPathYearlyController(
            CareerPathYearlyService service) {
        this.service = service;
    }

    @Operation(
            summary = "응답자별 연도별 직업코드 이력 조회",
            description = "KEEPⅡ 조사 데이터에서 2개 이상 연도의 유효한 직업코드가 관측된 응답자의 직업코드 이력을 조회합니다."
    )
    @GetMapping
    public List<String> getYearlyCareerPaths() throws Exception {
        return service.getYearlyCareerPaths();
    }

    @Operation(
            summary = "연도별 직업코드 변화 그래프 조회",
            description = "연도별 직업코드 관측 변화를 노드와 간선으로 반환합니다. "
                    + "출발 직업코드로 필터링할 수 있으며, "
                    + "관측된 직업코드 변화는 실제 이직 또는 첫 이직을 의미하지 않습니다."
    )
    @GetMapping("/graph")
    public ApiResponse<Map<String, Object>> getYearlyGraph(
            @Parameter(
                    description = "출발 직업코드(4자리 숫자). 생략하면 전체 그래프를 조회합니다.",
                    example = "3120"
            )
            @RequestParam(required = false) String fromCode
    ) throws Exception {

        return ApiResponse.success(
                service.getYearlyGraph(fromCode)
        );
    }
}
