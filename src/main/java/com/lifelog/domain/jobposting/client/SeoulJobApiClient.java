package com.lifelog.domain.jobposting.client;

import com.lifelog.domain.jobposting.exception.JobPostingErrorCode;
import com.lifelog.global.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// 서울 열린데이터광장 "서울시 일자리포털 채용 정보" (OA-13341, 서비스명 recMntList)
@Slf4j
@Component
public class SeoulJobApiClient {

    private static final String PATH = "/{key}/json/recMntList/{start}/{end}/";
    private static final String CODE_OK = "INFO-000";
    private static final String CODE_NO_DATA = "INFO-200";
    // API가 한 번에 허용하는 최대 건수
    private static final int MAX_PAGE_SIZE = 1000;

    private final RestClient restClient;
    private final String apiKey;
    private final int pageSize;

    @Autowired
    public SeoulJobApiClient(@Value("${seoul.openapi.base-url:http://openapi.seoul.go.kr:8088}") String baseUrl,
                             @Value("${seoul.openapi.key:}") String apiKey) {
        this(RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory()).build(), apiKey, MAX_PAGE_SIZE);
    }

    SeoulJobApiClient(RestClient restClient, String apiKey, int pageSize) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.pageSize = pageSize;
    }

    /**
     * 전체 공고를 pageSize 단위로 나눠 조회한다.
     * 전 페이지를 끝까지 받지 못하면(호출 실패, 오류 코드, 중간 페이지 누락, 건수 부족) EXTERNAL_API_FAILED.
     * 원본이 처음부터 0건(INFO-200)이면 빈 목록을 반환한다.
     */
    public List<SeoulJobRow> fetchAll() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("[SeoulJobApiClient] SEOUL_OPENAPI_KEY가 설정되지 않음");
            throw new CustomException(JobPostingErrorCode.EXTERNAL_API_FAILED);
        }

        List<SeoulJobRow> rows = new ArrayList<>();
        int start = 1;
        int totalCount = 0;
        while (true) {
            int end = start + pageSize - 1;
            SeoulJobApiResponse.Body body = fetchPage(start, end);
            if (body == null) {
                if (start == 1) {
                    log.info("[SeoulJobApiClient] 원본 공고 0건");
                    return List.of();
                }
                log.error("[SeoulJobApiClient] 중간 페이지에 데이터 없음 | start: {}, end: {}", start, end);
                throw new CustomException(JobPostingErrorCode.EXTERNAL_API_FAILED);
            }
            totalCount = body.totalCount();
            if (body.rows() != null) {
                rows.addAll(body.rows());
            }
            if (end >= totalCount) {
                break;
            }
            start = end + 1;
        }

        if (rows.size() < totalCount) {
            log.error("[SeoulJobApiClient] 받은 건수가 전체보다 적음 | rows: {}, total: {}", rows.size(), totalCount);
            throw new CustomException(JobPostingErrorCode.EXTERNAL_API_FAILED);
        }
        log.info("[SeoulJobApiClient] 채용공고 조회 완료 | rows: {}", rows.size());
        return rows;
    }

    // 데이터가 없으면 null
    private SeoulJobApiResponse.Body fetchPage(int start, int end) {
        SeoulJobApiResponse response;
        try {
            response = restClient.get()
                    .uri(PATH, apiKey, start, end)
                    .retrieve()
                    .body(SeoulJobApiResponse.class);
        } catch (RestClientException e) {
            // 예외 메시지에 인증키가 포함된 URL이 들어가므로 타입만 기록
            log.error("[SeoulJobApiClient] 호출 실패 | start: {}, end: {} | {}", start, end, e.getClass().getSimpleName());
            throw new CustomException(JobPostingErrorCode.EXTERNAL_API_FAILED);
        }

        SeoulJobApiResponse.Result result = response == null ? null
                : response.body() != null ? response.body().result() : response.result();
        if (result != null && CODE_NO_DATA.equals(result.code())) {
            return null;
        }
        if (result == null || !CODE_OK.equals(result.code()) || response.body() == null) {
            log.error("[SeoulJobApiClient] 오류 응답 | start: {}, end: {} | code: {}, message: {}",
                    start, end, result == null ? null : result.code(), result == null ? null : result.message());
            throw new CustomException(JobPostingErrorCode.EXTERNAL_API_FAILED);
        }
        return response.body();
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return factory;
    }
}
