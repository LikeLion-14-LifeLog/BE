
package com.lifelog.domain.careerpath.service;

import com.lifelog.domain.specprofile.service.SpecProfileQueryService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CareerPathPersonalizedService {

    private final SpecProfileQueryService specProfileQueryService;

    private static final int MIN_SAMPLE_SIZE = 5;

    private static final int[] YEARS = {
            2016, 2017, 2019, 2020,
            2021, 2022, 2023, 2024
    };

    private List<CareerSegment> segments = List.of();

    private Map<String, String> jobNames = Map.of();

    // 특정 연도에 관측된 직업 및 스펙 정보
    private record Observation(
            int year,
            String jobCode,
            String majorField,
            Long salary
    ) {}

    // 동일 응답자의 직업코드 변화 구간
    private record CareerSegment(
            int respondentIndex,
            String source,
            String target,
            int fromYear,
            int toYear,
            String majorField,
            Long fromSalary,
            Long toSalary
    ) {
        int yearGap() {
            return toYear - fromYear;
        }
    }

    @PostConstruct
    public void initializeCache() throws Exception {

        jobNames = Map.copyOf(loadJobNames());

        List<CareerSegment> loadedSegments = new ArrayList<>();

        try (CSVParser parser =
                     openCsv("data/KEEP2_YEARLY_SPEC_SALARY.csv")) {

            int respondentIndex = 0;

            for (CSVRecord record : parser) {

                respondentIndex++;

                Observation previous = null;
                String latestMajorField = "";

                for (int year : YEARS) {

                    String field = read(
                            record,
                            "UNIVERSITY_FIELD_" + year
                    );

                    // 해당 시점까지 확인된 최근 전공 계열
                    if (!field.isBlank()) {
                        latestMajorField = field;
                    }

                    String code = read(
                            record,
                            "KECO_CODE_" + year
                    );

                    if (!code.matches("\\d{4}")) {
                        continue;
                    }

                    Long salary = parseSalary(
                            read(
                                    record,
                                    "ANNUAL_SALARY_KRW_" + year
                            )
                    );

                    Observation current = new Observation(
                            year,
                            code,
                            latestMajorField,
                            salary
                    );

                    if (previous != null
                            && !previous.jobCode().equals(code)) {

                        loadedSegments.add(
                                new CareerSegment(
                                        respondentIndex,
                                        previous.jobCode(),
                                        current.jobCode(),
                                        previous.year(),
                                        current.year(),
                                        previous.majorField(),
                                        previous.salary(),
                                        current.salary()
                                )
                        );
                    }

                    previous = current;
                }
            }
        }

        segments = List.copyOf(loadedSegments);
    }

    private CSVParser openCsv(String path) throws Exception {

        ClassPathResource resource =
                new ClassPathResource(path);

        return CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get()
                .parse(
                        new InputStreamReader(
                                resource.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                );
    }

    private String read(CSVRecord record, String column) {

        if (!record.isMapped(column)
                || !record.isSet(column)) {
            return "";
        }

        return record.get(column).trim();
    }

    private Long parseSalary(String value) {

        try {

            long salary = new java.math.BigDecimal(value)
                    .longValueExact();

            return salary > 0 ? salary : null;

        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    private Map<String, String> loadJobNames() throws Exception {

        Map<String, String> result = new HashMap<>();

        try (CSVParser parser =
                     openCsv("data/job_2025.csv")) {

            for (CSVRecord record : parser) {

                String code = read(record, "code");
                String name = read(record, "name");

                if (code.matches("\\d{4}")
                        && !name.isBlank()) {

                    result.put(code, name);
                }
            }
        }

        return result;
    }

    private String jobName(String code) {

        return jobNames.getOrDefault(
                code,
                "직업코드 " + code
        );
    }


    // 현재 사용자의 전공 계열 조회
    private Set<String> getMajorFields(Long memberId) {

        var profile =
                specProfileQueryService.getCurrentProfile(memberId);

        Set<String> fields = new TreeSet<>();

        for (var academic : profile.academicAbilities()) {

            if (academic.majorAffiliation() != null) {

                fields.add(
                        academic.majorAffiliation().name()
                );

                fields.add(
                        academic.majorAffiliation().getKorean()
                );
            }
        }

        return fields;
    }

    // 직업코드 검증
    private void validateCode(String code) {

        if (code != null && !code.matches("\\d{4}")) {

            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "직업코드는 4자리 숫자여야 합니다."
            );
        }
    }

    // 사용자 전공 기반 관측 구간 필터링
    private List<CareerSegment> filterSegments(
            Set<String> fields,
            String fromCode
    ) {

        validateCode(fromCode);

        return segments.stream()
                .filter(s -> fields.contains(s.majorField()))
                .filter(s -> fromCode == null
                        || fromCode.equals(s.source()))
                .toList();
    }

    private long distinctRespondents(
            List<CareerSegment> items
    ) {

        return items.stream()
                .map(CareerSegment::respondentIndex)
                .distinct()
                .count();
    }

    private double round(double value) {

        return Math.round(value * 100.0) / 100.0;
    }

    // 공통 응답 정보
    private Map<String, Object> baseResponse(
            Set<String> fields
    ) {

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("dataStatus",
                "OBSERVED_OCCUPATION_CODE_CHANGE");

        result.put("verifiedFirstJobChange", false);

        result.put("matchingBasis", "MAJOR_FIELD");

        result.put("majorFields", fields);

        result.put("minSampleSize", MIN_SAMPLE_SIZE);

        result.put("notice",
                "현재 스펙의 전공 계열과 일치하는 응답자의 "
                        + "직업코드 관측 변화 통계입니다. "
                        + "실제 첫 취업 또는 첫 이직을 의미하지 않습니다.");

        return result;
    }

    // 출발·도착 직업 조합
    private record RouteKey(
            String source,
            String target
    ) {}

    private Map<RouteKey, List<CareerSegment>> groupRoutes(
            List<CareerSegment> items
    ) {

        Map<RouteKey, List<CareerSegment>> grouped =
                new LinkedHashMap<>();

        for (CareerSegment s : items) {

            RouteKey key = new RouteKey(
                    s.source(),
                    s.target()
            );

            grouped.computeIfAbsent(
                    key,
                    k -> new ArrayList<>()
            ).add(s);
        }

        return grouped;
    }

    // 개인화 경로 목록
    public Map<String, Object> getRoutes(
            Long memberId,
            String fromCode
    ) {

        Set<String> fields = getMajorFields(memberId);

        List<CareerSegment> filtered =
                filterSegments(fields, fromCode);

        Map<String, Long> sourceTotals = new HashMap<>();

        for (CareerSegment s : filtered) {

            sourceTotals.merge(
                    s.source(), 1L, Long::sum
            );
        }

        List<Map<String, Object>> routes = new ArrayList<>();

        for (var entry : groupRoutes(filtered).entrySet()) {

            RouteKey key = entry.getKey();

            List<CareerSegment> items = entry.getValue();

            long respondents = distinctRespondents(items);

            if (respondents < MIN_SAMPLE_SIZE) {
                continue;
            }

            Map<String, Object> route = new LinkedHashMap<>();

            route.put("id", key.source() + "-" + key.target());
            route.put("source", key.source());
            route.put("sourceName", jobName(key.source()));
            route.put("target", key.target());
            route.put("targetName", jobName(key.target()));

            route.put("observedSegments", items.size());

            route.put("distinctRespondents", respondents);

            route.put("observedYearIntervals",
                    items.stream()
                            .map(s -> s.fromYear() + "-" + s.toYear())
                            .distinct()
                            .count());

            route.put("shareOfSourceSegmentsPercent",
                    round(100.0 * items.size()
                            / sourceTotals.get(key.source())));

            routes.add(route);
        }

        routes.sort(
                Comparator.<Map<String, Object>>
                                comparingLong(r ->
                                ((Number) r.get("observedSegments"))
                                        .longValue())
                        .reversed()
                        .thenComparing(r -> (String) r.get("id"))
        );

        Map<String, Object> result = baseResponse(fields);

        result.put("status",
                fields.isEmpty() ? "SPEC_REQUIRED"
                        : routes.isEmpty() ? "INSUFFICIENT_DATA"
                          : "AVAILABLE");

        result.put("returnedRouteCount", routes.size());

        result.put("routes", routes);

        return result;
    }

    // 개인화 경로 그래프
    public Map<String, Object> getGraph(
            Long memberId,
            String fromCode
    ) {

        Map<String, Object> result =
                getRoutes(memberId, fromCode);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> routes =
                (List<Map<String, Object>>) result.get("routes");

        Set<String> codes = new TreeSet<>();

        for (Map<String, Object> route : routes) {

            codes.add((String) route.get("source"));
            codes.add((String) route.get("target"));
        }

        List<Map<String, String>> nodes = new ArrayList<>();

        for (String code : codes) {

            nodes.add(Map.of(
                    "id", code,
                    "code", code,
                    "label", jobName(code)
            ));
        }

        result.put("nodes", nodes);
        result.put("edges", routes);
        result.put("nodeCount", nodes.size());
        result.put("edgeCount", routes.size());

        result.remove("routes");

        return result;
    }

    // 개인화 경로 상세 분석
    public Map<String, Object> getAnalysis(
            Long memberId,
            String source,
            String target
    ) {

        validateCode(source);
        validateCode(target);

        Set<String> fields = getMajorFields(memberId);

        List<CareerSegment> matched =
                filterSegments(fields, source)
                        .stream()
                        .filter(s -> target.equals(s.target()))
                        .toList();

        Map<String, Object> result = baseResponse(fields);

        result.put("source", source);
        result.put("sourceName", jobName(source));
        result.put("target", target);
        result.put("targetName", jobName(target));

        long respondents = distinctRespondents(matched);

        if (respondents < MIN_SAMPLE_SIZE) {

            result.put("status",
                    fields.isEmpty()
                            ? "SPEC_REQUIRED"
                            : "INSUFFICIENT_DATA");

            result.put("salary", null);
            result.put("meanObservedYearGap", null);
            result.put("gapDistribution", List.of());

            return result;
        }

        result.put("status", "AVAILABLE");

        result.put("observedSegments", matched.size());

        result.put("distinctRespondents", respondents);

        // 연봉이 양쪽 시점 모두 존재하는 표본만 사용
        List<CareerSegment> salarySamples =
                matched.stream()
                        .filter(s -> s.fromSalary() != null
                                && s.toSalary() != null)
                        .toList();

        Map<String, Object> salary = new LinkedHashMap<>();

        if (distinctRespondents(salarySamples)
                >= MIN_SAMPLE_SIZE) {

            double fromMean = salarySamples.stream()
                    .mapToLong(s -> s.fromSalary())
                    .average()
                    .orElseThrow();

            double toMean = salarySamples.stream()
                    .mapToLong(s -> s.toSalary())
                    .average()
                    .orElseThrow();

            salary.put("status", "AVAILABLE");

            salary.put("sampleCount", salarySamples.size());

            salary.put("distinctRespondents",
                    distinctRespondents(salarySamples));

            salary.put("meanFromSalaryKrw",
                    Math.round(fromMean));

            salary.put("meanToSalaryKrw",
                    Math.round(toMean));

            salary.put("meanDifferenceKrw",
                    Math.round(toMean - fromMean));

            salary.put("meanSalaryChangePercent",
                    round((toMean - fromMean)
                            / fromMean * 100.0));

        } else {

            salary.put("status", "INSUFFICIENT_DATA");
            salary.put("meanFromSalaryKrw", null);
            salary.put("meanToSalaryKrw", null);
            salary.put("meanDifferenceKrw", null);
            salary.put("meanSalaryChangePercent", null);
        }

        result.put("salary", salary);

        // 평균 관측 연도 간격
        double meanGap = matched.stream()
                .mapToInt(CareerSegment::yearGap)
                .average()
                .orElseThrow();

        result.put("meanObservedYearGap", round(meanGap));

        // 관측 연도 간격 분포
        Map<Integer, List<CareerSegment>> gapGroups =
                new TreeMap<>();

        for (CareerSegment s : matched) {

            int bucket = Math.min(s.yearGap(), 4);

            gapGroups.computeIfAbsent(
                    bucket, k -> new ArrayList<>()
            ).add(s);
        }

        List<Map<String, Object>> distribution =
                new ArrayList<>();

        boolean hasSmallGroup = gapGroups.values().stream()
                .anyMatch(items ->
                        distinctRespondents(items) < MIN_SAMPLE_SIZE);

        if (!hasSmallGroup) {

            for (var entry : gapGroups.entrySet()) {

                distribution.add(Map.of(
                        "label",
                        entry.getKey() == 4
                                ? "4년 이상"
                                : entry.getKey() + "년",
                        "count", entry.getValue().size(),
                        "percent",
                        round(100.0 * entry.getValue().size()
                                / matched.size())
                ));
            }
        }

        result.put("gapDistribution", distribution);

        result.put("gapDistributionStatus",
                hasSmallGroup
                        ? "INSUFFICIENT_DATA"
                        : "AVAILABLE");

        return result;
    }

}
