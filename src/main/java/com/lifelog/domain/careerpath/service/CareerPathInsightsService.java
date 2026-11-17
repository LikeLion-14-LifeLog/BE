package com.lifelog.domain.careerpath.service;

import org.springframework.stereotype.Service;
import java.util.*;

/** Descriptive statistics for observed occupation-code changes, NOT verified job changes. */
@Service
public class CareerPathInsightsService {
    private final CareerPathYearlyService yearly;
    public CareerPathInsightsService(CareerPathYearlyService yearly) { this.yearly = yearly; }

    @SuppressWarnings("unchecked")
    private Map<String, Object> graph(String fromCode) throws Exception {
        return yearly.getYearlyGraph(fromCode);
    }

    private record Pair(String source, String target) { }
    private static final class Totals {
        long segments;
        int yearIntervals;
        Totals() { }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> routes(String fromCode, int minSegments) throws Exception {
        if (minSegments < 1 || minSegments > 100000) {
            throw new IllegalArgumentException("minSegments must be between 1 and 100000");
        }
        Map<String, Object> g = graph(fromCode);
        List<Map<String, Object>> edges = (List<Map<String, Object>>) g.get("edges");
        List<Map<String, String>> nodes = (List<Map<String, String>>) g.get("nodes");
        Map<String, String> names = new HashMap<>();
        for (Map<String, String> n : nodes) names.put(n.get("id"), n.get("label"));
        Map<Pair, Totals> grouped = new HashMap<>();
        Map<String, Long> sourceTotals = new HashMap<>();
        long allSegments = 0;
        for (Map<String, Object> edge : edges) {
            String source = (String) edge.get("source");
            String target = (String) edge.get("target");
            long count = ((Number) edge.get("distinctRespondents")).longValue();
            Totals t = grouped.computeIfAbsent(new Pair(source, target), k -> new Totals());
            t.segments += count;
            t.yearIntervals++;
            sourceTotals.merge(source, count, Long::sum);
            allSegments += count;
        }
        List<Map<String, Object>> routes = new ArrayList<>();
        for (Map.Entry<Pair, Totals> e : grouped.entrySet()) {
            Pair p = e.getKey();
            Totals t = e.getValue();
            if (t.segments < minSegments) continue;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("source", p.source());
            r.put("sourceName", names.getOrDefault(p.source(), "직업코드 " + p.source()));
            r.put("target", p.target());
            r.put("targetName", names.getOrDefault(p.target(), "직업코드 " + p.target()));
            r.put("observedSegments", t.segments);
            r.put("observedYearIntervals", t.yearIntervals);
            r.put("shareOfSourceSegmentsPercent", Math.round(10000.0 * t.segments / sourceTotals.get(p.source())) / 100.0);
            routes.add(r);
        }
        routes.sort(Comparator.<Map<String, Object>>comparingLong(r -> ((Number) r.get("observedSegments")).longValue())
                .reversed().thenComparing(r -> (String) r.get("source")).thenComparing(r -> (String) r.get("target")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataStatus", "OBSERVED_OCCUPATION_CODE_CHANGE");
        result.put("verifiedFirstJobChange", false);
        result.put("jobNameReferenceYear", 2025);
        result.put("notice", "직업코드 변화 관측 구간의 통계입니다. 실제 이직·첫 이직·고유 인원 비율이 아닙니다. 같은 사람이 여러 구간에 포함될 수 있습니다. 직업명은 2025년 분류표 기준입니다.");
        result.put("fromCode", fromCode == null ? "" : fromCode.trim());
        result.put("minSegments", minSegments);
        result.put("respondentCountInYearlyDataset", g.get("respondentCount"));
        result.put("allObservedSegments", allSegments);
        result.put("returnedRouteCount", routes.size());
        result.put("routes", routes);
        return result;
    }

    public Map<String, Object> routeDetail(String source, String target) throws Exception {
        if (source == null || !source.matches("\\d{4}") || target == null || !target.matches("\\d{4}")) {
            throw new IllegalArgumentException("source and target must be four-digit occupation codes");
        }
        Map<String, Object> summary = routes(source, 1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> routes = (List<Map<String, Object>>) summary.get("routes");
        Map<String, Object> route = routes.stream()
                .filter(r -> target.equals(r.get("target"))).findFirst().orElse(null);
        Map<String, Object> g = graph(source);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) g.get("edges");
        List<Map<String, Object>> intervals = new ArrayList<>();
        for (Map<String, Object> edge : edges) {
            if (source.equals(edge.get("source")) && target.equals(edge.get("target"))) {
                intervals.add(edge);
            }
        }
        intervals.sort(Comparator.comparingInt(e -> ((Number) e.get("fromYear")).intValue()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataStatus", "OBSERVED_OCCUPATION_CODE_CHANGE");
        result.put("verifiedFirstJobChange", false);
        result.put("route", route);
        result.put("yearlyIntervals", intervals);
        result.put("firstJobSalary", null);
        result.put("postChangeSalary", null);
        result.put("monthsToFirstJobChange", null);
        result.put("salaryDistribution", null);
        result.put("notice", "연봉·첫 이직 소요기간·분포는 검증된 첫 직장 및 이직 데이터가 없어 제공하지 않습니다.");
        return result;
    }
}
