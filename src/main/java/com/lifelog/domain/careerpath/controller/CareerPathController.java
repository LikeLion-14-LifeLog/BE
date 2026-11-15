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
    private final ObjectMapper objectMapper;

    public CareerPathController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private ObjectNode dataset() throws IOException {
        ClassPathResource resource = new ClassPathResource("data/career-paths.json");
        try (InputStream input = resource.getInputStream()) {
            return (ObjectNode) objectMapper.readTree(input);
        }
    }

    private ObjectNode response(ObjectNode data) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("success", true);
        result.put("code", "COMMON_200");
        result.put("message", "?붿껌???깃났?덉뒿?덈떎.");
        result.set("data", data);
        return result;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ObjectNode map() throws IOException {
        ObjectNode root = dataset();
        ObjectNode data = objectMapper.createObjectNode();
        data.put("title", root.path("title").asText());
        data.put("dataStatus", root.path("dataStatus").asText());
        data.put("notice", root.path("notice").asText());
        data.set("filters", root.path("filters").deepCopy());
        data.set("paths", root.path("paths").deepCopy());
        return response(data);
    }

    @GetMapping(value = "/{pathId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ObjectNode> detail(@PathVariable String pathId) throws IOException {
        ObjectNode root = dataset();
        ArrayNode paths = (ArrayNode) root.path("paths");
        for (var path : paths) {
            if (pathId.equals(path.path("id").asText())) {
                ObjectNode data = objectMapper.createObjectNode();
                data.put("dataStatus", root.path("dataStatus").asText());
                data.put("notice", root.path("notice").asText());
                data.set("path", path.deepCopy());
                data.set("detail", root.path("detail").deepCopy());
                return ResponseEntity.ok(response(data));
            }
        }
        ObjectNode error = objectMapper.createObjectNode();
        error.put("success", false);
        error.put("code", "CAREER_PATH_NOT_FOUND");
        error.put("message", "?대떦 而ㅻ━??寃쎈줈媛 ?놁뒿?덈떎.");
        error.putNull("data");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
}