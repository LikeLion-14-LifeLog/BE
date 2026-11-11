package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.dto.SeoulJobRow;
import com.lifelog.domain.jobposting.exception.JobPostingErrorCode;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.infra.seoul.SeoulOpenApiClient;
import com.lifelog.global.infra.seoul.SeoulOpenApiException;
import com.lifelog.global.infra.seoul.SeoulOpenApiPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 서울 일자리포털 채용정보(OA-13341, recMntList)를 받아 SeoulJobRow로 변환
@Slf4j
@Component
@RequiredArgsConstructor
public class SeoulJobPostingFetcher {

    static final String SERVICE = "recMntList";

    private final SeoulOpenApiClient seoulOpenApiClient;
    private final JsonMapper jsonMapper;

    // 페이지 호출이 하나라도 실패하면 EXTERNAL_API_FAILED
    public SeoulJobFetchResult fetch() {
        SeoulOpenApiPage page;
        try {
            page = seoulOpenApiClient.fetchAll(SERVICE);
        } catch (SeoulOpenApiException e) {
            throw new CustomException(JobPostingErrorCode.EXTERNAL_API_FAILED);
        }

        List<SeoulJobRow> rows = new ArrayList<>();
        Set<String> unreadable = new HashSet<>();
        for (JsonNode node : page.rows()) {
            try {
                rows.add(jsonMapper.treeToValue(node, SeoulJobRow.class));
            } catch (JacksonException e) {
                // 읽지 못해도 식별 필드로 externalId를 계산해 마감 대상에서 제외
                unreadable.add(SeoulJobPostingMapper.externalId(
                        text(node, "COMPANY"), text(node, "TITLE"), text(node, "REG_DT"), text(node, "WORK_REGION")));
                log.warn("[SeoulJobPostingFetcher] 행 읽기 실패 | {}", e.getClass().getSimpleName());
            }
        }
        return new SeoulJobFetchResult(rows, unreadable, page.isComplete());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isValueNode() ? "" : value.asString();
    }
}
