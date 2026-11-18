
package com.lifelog.domain.careerpath.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifelog.global.response.ApiResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/career-paths/exploratory")
public class CareerPathExploratoryController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // JSON 파일 읽기
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadData() throws IOException {
        ClassPathResource resource = new ClassPathResource(
                "data/career-paths-exploratory.json"
        );

        try (InputStream input = resource.getInputStream()) {
            return objectMapper.readValue(input, Map.class);
        }
    }

    // 탐색용 경로 전체 조회
    @GetMapping
    public ApiResponse<Map<String, Object>> exploratory()
            throws IOException {

        return ApiResponse.success(loadData());
    }

    // 탐색용 경로 상세 조회
    @GetMapping("/{pathId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(
            @PathVariable String pathId
    ) throws IOException {

        Map<String, Object> root = loadData();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> paths =
                (List<Map<String, Object>>) root.get("paths");

        for (Map<String, Object> path : paths) {

            if (pathId.equals(path.get("id"))) {

                Map<String, Object> data = new LinkedHashMap<>();

                data.put("dataStatus", root.get("dataStatus"));
                data.put("notice", root.get("notice"));
                data.put("path", path);

                return ResponseEntity.ok(
                        ApiResponse.success(data)
                );
            }
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<Map<String, Object>>(
                        false,
                        "CAREER_PATH_NOT_FOUND",
                        "해당 탐색용 커리어 경로를 찾을 수 없습니다.",
                        null
                ));
    }
}
