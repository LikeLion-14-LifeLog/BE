
package com.lifelog.domain.careerpath.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;

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

    private String response(ObjectNode data) throws IOException {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("success", true);
        result.put("code", "COMMON_200");
        result.put("message", "요청이 성공적으로 처리되었습니다.");
        result.set("data", data);

        return objectMapper.writeValueAsString(result);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> map() throws IOException {
        ObjectNode root = dataset();
        ObjectNode data = objectMapper.createObjectNode();

        data.put("title", root.path("title").asText());
        data.put("dataStatus", root.path("dataStatus").asText());
        data.put("notice", root.path("notice").asText());
        data.set("filters", root.path("filters").deepCopy());
        data.set("paths", root.path("paths").deepCopy());
        data.set("auditSummary", root.path("auditSummary").deepCopy());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response(data));
    }

    @GetMapping(
            value = "/{pathId}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> detail(
            @PathVariable String pathId
    ) throws IOException {

        ObjectNode root = dataset();
        ArrayNode paths = (ArrayNode) root.path("paths");

        for (var path : paths) {
            if (pathId.equals(path.path("id").asText())) {
                ObjectNode data = objectMapper.createObjectNode();

                data.put("dataStatus", root.path("dataStatus").asText());
                data.put("notice", root.path("notice").asText());
                data.set("path", path.deepCopy());
                data.set("detail", root.path("detail").deepCopy());

                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(response(data));
            }
        }

        ObjectNode error = objectMapper.createObjectNode();
        error.put("success", false);
        error.put("code", "CAREER_PATH_NOT_FOUND");
        error.put("message", "해당 커리어 경로를 찾을 수 없습니다.");
        error.putNull("data");

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(error));
    }
}
