
package com.lifelog.domain.careerpath.controller;

import com.lifelog.global.response.ApiResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@RestController
@RequestMapping("/api/v1/career-paths/exploratory")
public class CareerPathExploratoryGraphController {

    private final ObjectMapper objectMapper;

    public CareerPathExploratoryGraphController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GetMapping("/graph")
    public ApiResponse<Map<String, Object>> graph(
            @RequestParam(required = false) String fromCodePrefix) throws IOException {

        Map<String, Object> source;

        try (InputStream input = new ClassPathResource(
                "data/career-paths-exploratory.json"
        ).getInputStream()) {

            source = objectMapper.readValue(
                    input.readAllBytes(),
                    new TypeReference<Map<String, Object>>() {}
            );
        }

        Object rawPaths = source.get("paths");

        if (!(rawPaths instanceof List<?> allPaths)) {
            throw new IllegalStateException(
                    "Exploratory career path data is missing 'paths'"
            );
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> prefixes = new TreeSet<>();

        for (Object item : allPaths) {

            if (!(item instanceof Map<?, ?> path)) {
                continue;
            }

            String from = String.valueOf(path.get("fromCodePrefix"));
            String to = String.valueOf(path.get("toCodePrefix"));

            if (fromCodePrefix != null
                    && !fromCodePrefix.isBlank()
                    && !from.equals(fromCodePrefix)) {
                continue;
            }

            prefixes.add(from);
            prefixes.add(to);

            Map<String, Object> edge = new LinkedHashMap<>();

            edge.put("id", path.get("id"));
            edge.put("source", from);
            edge.put("target", to);
            edge.put("distinctRespondents", path.get("distinctRespondents"));
            edge.put("classification", "OCCUPATION_CODE_PREFIX_2_DIGITS");
            edge.put("verifiedFirstJobChange", false);

            edges.add(edge);
        }

        edges.sort(
                Comparator.comparing(
                        e -> String.valueOf(e.get("id"))
                )
        );

        List<Map<String, String>> nodes = new ArrayList<>();

        for (String prefix : prefixes) {
            nodes.add(
                    Map.of(
                            "id", prefix,
                            "label", "직업코드 " + prefix
                    )
            );
        }

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("dataStatus", "EXPLORATORY_NOT_VERIFIED");
        response.put("notice", source.get("notice"));
        response.put("countUnit", source.get("countUnit"));

        response.put(
                "filters",
                Map.of(
                        "fromCodePrefix",
                        fromCodePrefix == null ? "" : fromCodePrefix
                )
        );

        response.put("nodes", nodes);
        response.put("edges", edges);
        response.put("nodeCount", nodes.size());
        response.put("edgeCount", edges.size());
        response.put("auditSummary", source.get("auditSummary"));

        return ApiResponse.success(response);
    }
}
