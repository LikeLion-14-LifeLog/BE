package com.lifelog.domain.careerpath.controller;

import com.lifelog.domain.careerpath.service.CareerPathInsightsService;
import com.lifelog.global.response.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/career-paths/yearly")
public class CareerPathInsightsController {
    private final CareerPathInsightsService service;
    public CareerPathInsightsController(CareerPathInsightsService service) { this.service = service; }

    @GetMapping("/routes")
    public ApiResponse<Map<String, Object>> routes(
            @RequestParam(required = false) String fromCode,
            @RequestParam(defaultValue = "5") int minSegments) throws Exception {
        return ApiResponse.success(service.routes(fromCode, minSegments));
    }

    @GetMapping("/routes/{source}/{target}")
    public ApiResponse<Map<String, Object>> detail(
            @PathVariable String source, @PathVariable String target) throws Exception {
        return ApiResponse.success(service.routeDetail(source, target));
    }
}
