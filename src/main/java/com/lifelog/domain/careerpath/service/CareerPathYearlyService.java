
package com.lifelog.domain.careerpath.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class CareerPathYearlyService {

    private static final int[] YEARS = {
            2016, 2017, 2019, 2020,
            2021, 2022, 2023, 2024
    };

    // CSV 파일을 헤더 기준으로 읽는 공통 메서드
    private CSVParser openCsv(String path) throws Exception {

        ClassPathResource resource = new ClassPathResource(path);

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();

        return format.parse(
                new InputStreamReader(
                        resource.getInputStream(),
                        StandardCharsets.UTF_8
                )
        );
    }

    // 1. 응답자별 연도별 직업코드 이력 추출
    public List<String> getYearlyCareerPaths() throws Exception {

        List<String> results = new ArrayList<>();

        try (CSVParser parser =
                     openCsv("data/KEEP2_YEARLY_SPEC_SALARY.csv")) {

            for (CSVRecord record : parser) {

                List<String> history = new ArrayList<>();

                for (int year : YEARS) {

                    String column = "KECO_CODE_" + year;

                    if (!record.isMapped(column)
                            || !record.isSet(column)) {
                        continue;
                    }

                    String code = record.get(column).trim();

                    if (!code.matches("\\d{4}")) {
                        continue;
                    }

                    history.add(year + ":" + code);
                }

                if (history.size() >= 2) {
                    results.add(String.join(" -> ", history));
                }
            }
        }

        return results;
    }

    // 2. 2025년 기준 직업코드-직업명 매핑
    private Map<String, String> loadJobNames() throws Exception {

        Map<String, String> jobNames = new LinkedHashMap<>();

        try (CSVParser parser = openCsv("data/job_2025.csv")) {

            for (CSVRecord record : parser) {

                String code = record.get("code").trim();
                String name = record.get("name").trim();

                if (code.matches("\\d{4}") && !name.isEmpty()) {
                    jobNames.put(code, name);
                }
            }
        }

        return jobNames;
    }

    // 3. 기존 전체 그래프 조회
    public Map<String, Object> getYearlyGraph() throws Exception {
        return getYearlyGraph(null);
    }

    // 4. 직업명 매핑 + 출발 직업코드 필터 그래프
    public Map<String, Object> getYearlyGraph(String fromCode)
            throws Exception {

        String filterCode =
                fromCode == null ? "" : fromCode.trim();

        if (!filterCode.isEmpty()
                && !filterCode.matches("\\d{4}")) {
            throw new IllegalArgumentException(
                    "fromCode는 4자리 숫자여야 합니다."
            );
        }

        List<String> histories = getYearlyCareerPaths();
        Map<String, String> jobNames = loadJobNames();

        Map<String, Set<Integer>> edgeRespondents =
                new LinkedHashMap<>();

        Set<String> nodeCodes = new TreeSet<>();

        for (int respondentIndex = 0;
             respondentIndex < histories.size();
             respondentIndex++) {

            String[] observations =
                    histories.get(respondentIndex).split(" -> ");

            String previousCode = null;
            int previousYear = 0;

            for (String observation : observations) {

                String[] parts = observation.split(":");

                int year = Integer.parseInt(parts[0]);
                String code = parts[1];

                // 전체 조회에서는 기존처럼 모든 관측 코드 유지
                if (filterCode.isEmpty()) {
                    nodeCodes.add(code);
                }

                if (previousCode != null
                        && !previousCode.equals(code)) {

                    boolean matchesFilter =
                            filterCode.isEmpty()
                                    || previousCode.equals(filterCode);

                    if (matchesFilter) {

                        String edgeKey =
                                previousCode + "|" + code
                                        + "|" + previousYear + "|" + year;

                        edgeRespondents
                                .computeIfAbsent(
                                        edgeKey,
                                        k -> new HashSet<>()
                                )
                                .add(respondentIndex);

                        nodeCodes.add(previousCode);
                        nodeCodes.add(code);
                    }
                }

                previousCode = code;
                previousYear = year;
            }
        }

        // 5. 간선 생성
        List<Map<String, Object>> edges = new ArrayList<>();

        for (Map.Entry<String, Set<Integer>> entry
                : edgeRespondents.entrySet()) {

            String[] parts = entry.getKey().split("\\|");

            Map<String, Object> edge = new LinkedHashMap<>();

            edge.put("id", entry.getKey());
            edge.put("source", parts[0]);
            edge.put("target", parts[1]);
            edge.put("fromYear", Integer.parseInt(parts[2]));
            edge.put("toYear", Integer.parseInt(parts[3]));
            edge.put("distinctRespondents", entry.getValue().size());
            edge.put("verifiedFirstJobChange", false);

            edges.add(edge);
        }

        edges.sort(
                Comparator.comparing(
                        e -> String.valueOf(e.get("id"))
                )
        );

        // 6. 직업명을 포함한 노드 생성
        List<Map<String, String>> nodes = new ArrayList<>();

        for (String code : nodeCodes) {

            Map<String, String> node = new LinkedHashMap<>();

            node.put("id", code);
            node.put("code", code);
            node.put(
                    "label",
                    jobNames.getOrDefault(code, "직업코드 " + code)
            );

            nodes.add(node);
        }

        // 7. API 응답 생성
        Map<String, Object> result = new LinkedHashMap<>();

        result.put(
                "dataStatus",
                "OBSERVED_OCCUPATION_CODE_CHANGE"
        );

        result.put(
                "notice",
                "연도별 직업코드 관측 변화이며 실제 이직 또는 첫 이직을 의미하지 않습니다."
        );

        result.put("classification", "OCCUPATION_CODE_4_DIGITS");
        result.put("jobNameReferenceYear", 2025);
        result.put("verifiedFirstJobChange", false);
        result.put("respondentCount", histories.size());

        result.put(
                "filters",
                Map.of("fromCode", filterCode)
        );

        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("nodeCount", nodes.size());
        result.put("edgeCount", edges.size());

        return result;
    }
}
