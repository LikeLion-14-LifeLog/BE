
package com.lifelog.domain.careerpath.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lifelog.global.response.ApiResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/career-paths")
public class CareerPathController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ObjectNode dataset() throws IOException {
        ClassPathResource resource =
                new ClassPathResource("data/career-paths.json");

        try (InputStream input = resource.getInputStream()) {
            return (ObjectNode) objectMapper.readTree(input);
        }
    }

    private Object toJavaObject(ObjectNode node) {
        return objectMapper.convertValue(node, Object.class);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> map()
            throws IOException {

        ObjectNode root = dataset();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", root.path("title").asText());
        data.put("dataStatus", root.path("dataStatus").asText());
        data.put("notice", root.path("notice").asText());
        data.put("filters",
                objectMapper.convertValue(root.path("filters"), Object.class));
        data.put("paths",
                objectMapper.convertValue(root.path("paths"), Object.class));
        data.put("auditSummary",
                objectMapper.convertValue(root.path("auditSummary"), Object.class));

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/{pathId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(
            @PathVariable String pathId) throws IOException {

        ObjectNode root = dataset();
        ArrayNode paths = (ArrayNode) root.path("paths");

        for (var path : paths) {
            if (pathId.equals(path.path("id").asText())) {

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("dataStatus", root.path("dataStatus").asText());
                data.put("notice", root.path("notice").asText());
                data.put("path",
                        objectMapper.convertValue(path, Object.class));
                data.put("detail",
                        objectMapper.convertValue(root.path("detail"), Object.class));

                return ResponseEntity.ok(ApiResponse.success(data));
            }
        }

        ApiResponse<Map<String, Object>> error =
                new ApiResponse<>(
                        false,
                        "CAREER_PATH_NOT_FOUND",
                        "해당 커리어 경로를 찾을 수 없습니다.",
                        null
                );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
}
