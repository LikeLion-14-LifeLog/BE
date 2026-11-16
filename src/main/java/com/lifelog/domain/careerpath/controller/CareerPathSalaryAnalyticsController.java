package com.lifelog.domain.careerpath.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/career-paths/yearly")
public class CareerPathSalaryAnalyticsController {
    private final Map<String, Object> data;

    public CareerPathSalaryAnalyticsController(ObjectMapper mapper) throws Exception {
        try (InputStream stream = new ClassPathResource("data/career-path-salary-analytics.json").getInputStream()) {
            this.data = mapper.readValue(stream, new TypeReference<Map<String, Object>>() {});
        }
    }

    @GetMapping("/salary-analytics")
    public ResponseEntity<Map<String, Object>> salaryAnalytics(
            @RequestParam(required = false) String fromCode,
            @RequestParam(required = false) String toCode,
            @RequestParam(required = false) String majorField,
            @RequestParam(defaultValue = "100") int limit) {
        if (limit < 1 || limit > 500) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "limit must be between 1 and 500"));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> all = (List<Map<String, Object>>) data.get("groups");
        List<Map<String, Object>> filtered = all.stream()
                .filter(g -> fromCode == null || fromCode.equals(g.get("source")))
                .filter(g -> toCode == null || toCode.equals(g.get("target")))
                .filter(g -> majorField == null || majorField.equals(g.get("majorField")))
                .sorted(Comparator.comparingInt((Map<String, Object> g) -> ((Number) g.get("segmentCount")).intValue()).reversed())
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("metadata", data.get("metadata"));
        payload.put("filters", Map.of("fromCode", fromCode == null ? "" : fromCode,
                "toCode", toCode == null ? "" : toCode,
                "majorField", majorField == null ? "" : majorField));
        payload.put("matchingGroupCount", filtered.size());
        payload.put("groups", filtered.stream().limit(limit).toList());
        return ResponseEntity.ok(Map.of("success", true, "message", "관측 직업코드 변경 구간 연봉 통계 조회 성공", "data", payload));
    }
}
