package com.lifelog.domain.jobposting.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// 정상: {"recMntList": {"list_total_count", "RESULT", "row"}}, 오류: {"RESULT": {...}}
@JsonIgnoreProperties(ignoreUnknown = true)
record SeoulJobApiResponse(
        @JsonProperty("recMntList") Body body,
        @JsonProperty("RESULT") Result result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Body(
            @JsonProperty("list_total_count") int totalCount,
            @JsonProperty("RESULT") Result result,
            @JsonProperty("row") List<SeoulJobRow> rows
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(
            @JsonProperty("CODE") String code,
            @JsonProperty("MESSAGE") String message
    ) {
    }
}
