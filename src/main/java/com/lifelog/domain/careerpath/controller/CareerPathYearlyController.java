
package com.lifelog.domain.careerpath.controller;

import com.lifelog.domain.careerpath.service.CareerPathYearlyService;
import com.lifelog.global.response.ApiResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/career-paths/yearly")
public class CareerPathYearlyController {

    private final CareerPathYearlyService service;

    public CareerPathYearlyController(
            CareerPathYearlyService service) {
        this.service = service;
    }

    // 응답자별 연도별 직업코드 이력 조회
    @GetMapping
    public List<String> getYearlyCareerPaths() throws Exception {
        return service.getYearlyCareerPaths();
    }

    // 연도별 직업코드 변화 그래프 조회
    // fromCode는 선택 사항
    @GetMapping("/graph")
    public ApiResponse<Map<String, Object>> getYearlyGraph(
            @RequestParam(required = false) String fromCode
    ) throws Exception {

        return ApiResponse.success(
                service.getYearlyGraph(fromCode)
        );
    }
}
