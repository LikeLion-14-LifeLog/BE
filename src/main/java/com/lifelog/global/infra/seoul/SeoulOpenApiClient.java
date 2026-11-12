package com.lifelog.global.infra.seoul;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 서울 열린데이터광장 Open API 공통 클라이언트.
 * http://openapi.seoul.go.kr:8088/{KEY}/json/{서비스명}/{시작}/{끝}/ 형식을 호출하고 행을 JsonNode로 반환한다.
 * 특정 도메인의 응답 형식은 알지 못한다.
 */
@Slf4j
@Component
public class SeoulOpenApiClient {

    // API가 한 번에 허용하는 최대 건수
    public static final int MAX_PAGE_SIZE = 1000;

    private static final String PATH = "/{key}/json/{service}/{start}/{end}/";
    private static final String CODE_OK = "INFO-000";
    private static final String CODE_NO_DATA = "INFO-200";

    private final RestClient restClient;
    private final String apiKey;
    private final int pageSize;

    @Autowired
    public SeoulOpenApiClient(RestClient.Builder restClientBuilder,
                              @Value("${seoul.openapi.base-url:http://openapi.seoul.go.kr:8088}") String baseUrl,
                              @Value("${seoul.openapi.key:}") String apiKey) {
        this(restClientBuilder.baseUrl(baseUrl).build(), apiKey, MAX_PAGE_SIZE);
    }

    SeoulOpenApiClient(RestClient restClient, String apiKey, int pageSize) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.pageSize = pageSize;
    }

    /**
     * 전체 데이터를 pageSize 단위로 나눠 조회한다.
     * 호출이 하나라도 실패하면 SeoulOpenApiException. 중간 페이지가 비어 있으면 거기까지 받은 결과를 반환하며,
     * 전체 건수만큼 받았는지는 {@link SeoulOpenApiPage#isComplete()}로 확인한다.
     */
    public SeoulOpenApiPage fetchAll(String service) {
        List<JsonNode> rows = new ArrayList<>();
        int totalCount = 0;
        int start = 1;
        while (true) {
            int end = start + pageSize - 1;
            SeoulOpenApiPage page = fetchRange(service, start, end);
            if (page.rows().isEmpty()) {
                if (start > 1) {
                    log.warn("[SeoulOpenApiClient] 중간 페이지에 데이터 없음 | service: {}, start: {}, end: {}", service, start, end);
                }
                break;
            }
            totalCount = page.totalCount();
            rows.addAll(page.rows());
            if (end >= totalCount) {
                break;
            }
            start = end + 1;
        }

        log.info("[SeoulOpenApiClient] 조회 완료 | service: {}, rows: {}, total: {}", service, rows.size(), totalCount);
        return new SeoulOpenApiPage(totalCount, rows);
    }

    /**
     * start~end 범위(1부터, 최대 1000건)를 한 번 조회한다. 데이터가 없으면(INFO-200) 빈 결과.
     */
    public SeoulOpenApiPage fetchRange(String service, int start, int end) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("[SeoulOpenApiClient] SEOUL_OPENAPI_KEY가 설정되지 않음");
            throw new SeoulOpenApiException("서울 열린데이터 인증키가 설정되지 않았습니다.");
        }
        if (start < 1 || end < start || end - start + 1 > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("조회 범위가 올바르지 않습니다: " + start + "~" + end);
        }

        JsonNode root;
        try {
            root = restClient.get()
                    .uri(PATH, apiKey, service, start, end)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            // 예외 메시지에 인증키가 포함된 URL이 들어가므로 타입만 기록
            log.error("[SeoulOpenApiClient] 호출 실패 | service: {}, start: {}, end: {} | {}",
                    service, start, end, e.getClass().getSimpleName());
            throw new SeoulOpenApiException("서울 열린데이터 호출에 실패했습니다.");
        }

        // 정상: {"서비스명": {"list_total_count", "RESULT", "row"}}, 오류·데이터 없음: {"RESULT": {...}}
        JsonNode body = root == null ? null : root.get(service);
        JsonNode result = body != null ? body.get("RESULT") : root == null ? null : root.get("RESULT");
        String code = result == null ? null : result.path("CODE").asString();

        if (CODE_NO_DATA.equals(code)) {
            return SeoulOpenApiPage.empty();
        }
        if (!CODE_OK.equals(code) || body == null) {
            log.error("[SeoulOpenApiClient] 오류 응답 | service: {}, start: {}, end: {} | code: {}, message: {}",
                    service, start, end, code, result == null ? null : result.path("MESSAGE").asString());
            throw new SeoulOpenApiException("서울 열린데이터 오류 응답: " + code);
        }

        List<JsonNode> rows = new ArrayList<>();
        body.path("row").forEach(rows::add);
        return new SeoulOpenApiPage(body.path("list_total_count").asInt(), rows);
    }
}
