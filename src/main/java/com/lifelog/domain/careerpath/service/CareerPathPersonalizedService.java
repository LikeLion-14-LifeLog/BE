package com.lifelog.domain.careerpath.service;

import com.lifelog.domain.specprofile.service.SpecProfileQueryService;
import com.lifelog.domain.certification.repository.CertificationRepository;
import com.lifelog.domain.specprofile.dto.SpecProfileResDto;
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
    private final CertificationRepository certificationRepository;

    private static final int MIN_SAMPLE_SIZE = 5;

    private static final int[] YEARS = {
            2016, 2017, 2019, 2020,
            2021, 2022, 2023, 2024
    };

    private List<CareerSegment> segments = List.of();

    private Map<String, String> jobNames = Map.of();
    private List<SpecObservation> specObservations = List.of();
    private record SpecObservation(int respondent, String job, int year, Map<String,String> specs) {}

    // 특정 연도에 관측된 직업 및 스펙 정보
    private record Observation(
            int year,
            String jobCode,
            String majorField,
            Long salary,
            Map<String,String> specs
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
            Long toSalary,
            Map<String,String> fromSpecs
    ) {
        int yearGap() {
            return toYear - fromYear;
        }
    }

    @PostConstruct
    public void initializeCache() throws Exception {

        jobNames = Map.copyOf(loadJobNames());

        List<CareerSegment> loadedSegments = new ArrayList<>();
        List<SpecObservation> loadedSpecs = new ArrayList<>();

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
                            salary,
                            readSpecs(record, year, latestMajorField)
                    );
                    loadedSpecs.add(new SpecObservation(respondentIndex, code, year, current.specs()));

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
                                        current.salary(),
                                        previous.specs()
                                )
                        );
                    }

                    previous = current;
                }
            }
        }

        segments = List.copyOf(loadedSegments);
        specObservations = List.copyOf(loadedSpecs);
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
    private static final int MIN_P75_SAMPLE_SIZE = 10;

    // 선형 보간 방식 백분위수 계산
    private double percentile(long[] values, double p) {

        if (values.length == 0) {
            throw new IllegalArgumentException("표본이 없습니다.");
        }

        if (!Double.isFinite(p) || p < 0 || p > 1) {
            throw new IllegalArgumentException("잘못된 백분위수입니다.");
        }

        long[] sorted = values.clone();
        Arrays.sort(sorted);

        double index = (sorted.length - 1) * p;

        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);

        if (lower == upper) {
            return sorted[lower];
        }

        double weight = index - lower;

        return sorted[lower]
                + weight * (sorted[upper] - sorted[lower]);
    }

    private Map<String, Object> calculateSalaryStats(
            List<CareerSegment> items
    ) {

        List<CareerSegment> salarySamples = items.stream()
                .filter(s -> s.fromSalary() != null
                        && s.toSalary() != null)
                .toList();

        long respondentCount =
                distinctRespondents(salarySamples);

        Map<String, Object> salary = new LinkedHashMap<>();

        // 표본 부족 시에도 신규 필드 구조 유지
        salary.put("status", "INSUFFICIENT_DATA");
        salary.put("sampleCount", null);
        salary.put("distinctRespondents", null);

        salary.put("meanFromSalaryKrw", null);
        salary.put("meanToSalaryKrw", null);
        salary.put("meanDifferenceKrw", null);
        salary.put("meanSalaryChangePercent", null);

        salary.put("medianFromSalaryKrw", null);
        salary.put("medianToSalaryKrw", null);

        salary.put("p75FromSalaryKrw", null);
        salary.put("p75ToSalaryKrw", null);
        salary.put("p75DifferenceKrw", null);
        salary.put("p75MinSampleSize", MIN_P75_SAMPLE_SIZE);
        salary.put("p75Status", "INSUFFICIENT_DATA");

        if (respondentCount < MIN_SAMPLE_SIZE) {
            return salary;
        }

        long[] fromValues = salarySamples.stream()
                .mapToLong(s -> s.fromSalary())
                .toArray();

        long[] toValues = salarySamples.stream()
                .mapToLong(s -> s.toSalary())
                .toArray();

        double fromMean = Arrays.stream(fromValues)
                .average()
                .orElseThrow();

        double toMean = Arrays.stream(toValues)
                .average()
                .orElseThrow();

        salary.put("status", "AVAILABLE");
        salary.put("sampleCount", salarySamples.size());
        salary.put("distinctRespondents", respondentCount);

        salary.put("meanFromSalaryKrw",
                Math.round(fromMean));

        salary.put("meanToSalaryKrw",
                Math.round(toMean));

        salary.put("meanDifferenceKrw",
                Math.round(toMean - fromMean));

        salary.put("meanSalaryChangePercent",
                round((toMean - fromMean) / fromMean * 100.0));

        // 중앙값
        salary.put("medianFromSalaryKrw",
                Math.round(percentile(fromValues, 0.5)));

        salary.put("medianToSalaryKrw",
                Math.round(percentile(toValues, 0.5)));

        // 상위 25% 진입 기준 연봉
        if (respondentCount >= MIN_P75_SAMPLE_SIZE) {

            double fromP75 = percentile(fromValues, 0.75);
            double toP75 = percentile(toValues, 0.75);

            salary.put("p75Status", "AVAILABLE");

            salary.put("p75FromSalaryKrw",
                    Math.round(fromP75));

            salary.put("p75ToSalaryKrw",
                    Math.round(toP75));

            salary.put("p75DifferenceKrw",
                    Math.round(toP75 - fromP75));
        }

        return salary;
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

            result.put("salary", calculateSalaryStats(List.of()));
            result.put("meanObservedYearGap", null);
            result.put("gapDistribution", List.of());
            result.put("gapDistributionStatus", "INSUFFICIENT_DATA");
            result.put("requirements", specResult("TARGET_OCCUPATION_OBSERVED_SPEC", List.of(), Set.of(), false));
            result.put("recommendations", specResult("SOURCE_OBSERVATION_SPEC", List.of(), Set.of(), true));

            return result;
        }

        result.put("status", "AVAILABLE");

        result.put("observedSegments", matched.size());

        result.put("distinctRespondents", respondents);

        result.put("salary", calculateSalaryStats(matched));

        var profile = specProfileQueryService.getCurrentProfile(memberId);
        Set<String> owned = ownedSpecs(profile);
        // 직업별 가장 최근 관측, 경로별 최초 출발 관측: 응답자당 한 건
        Map<Integer, Map<String,String>> targetSpecs = new LinkedHashMap<>();
        specObservations.stream().filter(o -> target.equals(o.job()))
                .sorted(Comparator.comparingInt(SpecObservation::year))
                .forEach(o -> targetSpecs.put(o.respondent(), o.specs()));
        Map<Integer, Map<String,String>> sourceSpecs = new LinkedHashMap<>();
        matched.stream().sorted(Comparator.comparingInt(CareerSegment::fromYear))
                .forEach(o -> sourceSpecs.putIfAbsent(o.respondentIndex(), o.fromSpecs()));
        result.put("requirements", specResult("TARGET_OCCUPATION_OBSERVED_SPEC", targetSpecs.values(), owned, false));
        result.put("recommendations", specResult("SOURCE_OBSERVATION_SPEC", sourceSpecs.values(), owned, true));

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

    // 전체 경로 연봉 분석 (전공 계열 필터 없이 조회)
    public Map<String, Object> getOverallSalaryAnalysis(
            String source,
            String target
    ) {

        validateCode(source);
        validateCode(target);

        List<CareerSegment> matched = segments.stream()
                .filter(s -> source.equals(s.source()))
                .filter(s -> target.equals(s.target()))
                .toList();

        long respondents = distinctRespondents(matched);

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("dataStatus",
                "OBSERVED_OCCUPATION_CODE_CHANGE");

        result.put("verifiedFirstJobChange", false);
        result.put("matchingBasis", "ALL_RESPONDENTS");
        result.put("minSampleSize", MIN_SAMPLE_SIZE);

        result.put("notice",
                "전체 응답자의 직업코드 관측 변화에 대한 연봉 통계입니다. "
                        + "실제 첫 취업 또는 첫 이직 연봉을 의미하지 않습니다.");

        result.put("source", source);
        result.put("sourceName", jobName(source));

        result.put("target", target);
        result.put("targetName", jobName(target));

        if (respondents < MIN_SAMPLE_SIZE) {

            result.put("status", "INSUFFICIENT_DATA");
            result.put("observedSegments", null);
            result.put("distinctRespondents", null);

            result.put("salary",
                    calculateSalaryStats(List.of()));

            return result;
        }

        result.put("status", "AVAILABLE");
        result.put("observedSegments", matched.size());
        result.put("distinctRespondents", respondents);

        result.put("salary", calculateSalaryStats(matched));

        return result;
    }

    // CURRENT 스펙에 등록된 자격증의 유형 조회
    private Set<String> getOwnedCertificateTypes(SpecProfileResDto profile) {
        if (profile.certificates() == null || profile.certificates().isEmpty()) {
            return Set.of();
        }
        List<Long> certificateIds = profile.certificates().stream()
                .map(certificate -> certificate.certificateId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Set<String> types = new HashSet<>();
        certificationRepository.findAllById(certificateIds)
                .forEach(certificate -> {
                    if (certificate.getType() != null) {
                        types.add(certificate.getType().name());
                    }
                });
        return types;
    }

    private Map<String,String> readSpecs(CSVRecord row, int year, String major) {
        Map<String,String> specs = new HashMap<>();
        if (!major.isBlank()) specs.put("MAJOR_FIELD", major);
        String exam = read(row, "ENGLISH_TEST_TYPE_" + year);
        if (Set.of("NONE","TOEIC","OPIC","TOEIC_SPEAKING","TOEIC_WRITING","TOEFL","TEPS","IELTS","OTHER").contains(exam))
            specs.put("LANGUAGE_EXAM", exam);
        readCount(row, year, specs, "CERTIFICATE_TOTAL_COUNT", "CERTIFICATE_ANY");
        readCount(row, year, specs, "NATIONAL_TECHNICAL_CERTIFICATE_COUNT", "NATIONAL_TECHNICAL");
        readCount(row, year, specs, "NATIONAL_PROFESSIONAL_CERTIFICATE_COUNT", "NATIONAL_PROFESSIONAL");
        return Map.copyOf(specs);
    }

    private void readCount(CSVRecord row, int year, Map<String,String> specs, String column, String key) {
        try {
            int n = Integer.parseInt(read(row, column + "_" + year));
            if (n >= 0) specs.put(key, n > 0 ? "YES" : "NO");
        } catch (NumberFormatException ignored) { }
    }

    private String normalizeExam(String name) {
        return name == null ? "" : name.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private Set<String> ownedSpecs(SpecProfileResDto profile) {
        Set<String> owned = new HashSet<>();
        for (var a : profile.academicAbilities()) {
            if (a.majorAffiliation() != null) owned.add("MAJOR_FIELD:" + a.majorAffiliation().name());
        }
        for (var exam : profile.languageExamScores()) {
            owned.add("LANGUAGE_EXAM:" + normalizeExam(exam.languageExamName()));
        }
        if (!profile.certificates().isEmpty()) owned.add("CERTIFICATE_ANY:YES");
        for (String type : getOwnedCertificateTypes(profile)) owned.add(type + ":YES");
        return owned;
    }

    private Map<String,Object> specResult(String basis, Collection<Map<String,String>> rows,
                                          Set<String> owned, boolean onlyUnmatched) {
        List<Map<String,Object>> items = new ArrayList<>();
        for (String key : List.of("MAJOR_FIELD", "LANGUAGE_EXAM", "CERTIFICATE_ANY", "NATIONAL_TECHNICAL", "NATIONAL_PROFESSIONAL")) {
            Map<String,Long> counts = new HashMap<>();
            for (var row : rows) {
                String value = row.get(key);
                if (value != null) counts.merge(value, 1L, Long::sum);
            }
            long valid = counts.values().stream().mapToLong(Long::longValue).sum();
            for (var entry : counts.entrySet()) {
                String value = entry.getKey();
                long count = entry.getValue();
                // 소수 집단 및 보완 집단 노출 방지
                if (count < MIN_SAMPLE_SIZE || valid - count < MIN_SAMPLE_SIZE) continue;
                if (Set.of("NONE", "OTHER", "NO").contains(value)) continue;
                boolean matched = owned.contains(key + ":" + value);
                if (onlyUnmatched && matched) continue;
                String type = key.equals("CERTIFICATE_ANY") ? "CERTIFICATE" :
                        key.startsWith("NATIONAL_") ? "CERTIFICATE_TYPE" : key;
                String code = type.equals("CERTIFICATE_TYPE") || key.equals("CERTIFICATE_ANY") ? key : value;
                String name = switch (code) {
                    case "CERTIFICATE_ANY" -> "자격증 보유";
                    case "NATIONAL_TECHNICAL" -> "국가기술자격증";
                    case "NATIONAL_PROFESSIONAL" -> "국가전문자격증";
                    case "ENGINEERING" -> "공학계열";
                    case "HUMANITIES" -> "인문계열";
                    case "SOCIAL_SCIENCES" -> "사회계열";
                    case "NATURAL_SCIENCES" -> "자연계열";
                    case "MEDICINE_PHARMACY" -> "의약학계열";
                    case "EDUCATION" -> "교육계열";
                    case "ARTS_PHYSICAL_EDUCATION" -> "예체능계열";
                    default -> code;
                };
                Map<String,Object> item = new LinkedHashMap<>();
                item.put("type", type); item.put("code", code); item.put("name", name);
                item.put("matched", matched); item.put("sampleCount", valid);
                item.put("observedCount", count); item.put("sharePercent", round(100.0 * count / valid));
                items.add(item);
            }
        }
        items.sort(Comparator.<Map<String,Object>>comparingDouble(i -> ((Number)i.get("sharePercent")).doubleValue()).reversed().thenComparing(i -> (String)i.get("code")));
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("status", items.isEmpty() ? "NO_REPORTABLE_ITEMS" : "AVAILABLE");
        result.put("basis", basis);
        result.put("minSampleSize", MIN_SAMPLE_SIZE);
        result.put("notice", "응답자별 한 관측 시점을 사용하며 항목별 유효 응답 기준 비율입니다. 필수 요건이나 성공 원인이 아닙니다. matched는 현재 등록 스펙 기준입니다.");
        result.put("items", items);
        return result;
    }
}
