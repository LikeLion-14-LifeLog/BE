package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.exception.JobPostingErrorCode;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.infra.seoul.SeoulOpenApiClient;
import com.lifelog.global.infra.seoul.SeoulOpenApiException;
import com.lifelog.global.infra.seoul.SeoulOpenApiPage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class SeoulJobPostingFetcherTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final SeoulOpenApiClient client = mock(SeoulOpenApiClient.class);
    private final SeoulJobPostingFetcher fetcher = new SeoulJobPostingFetcher(client, jsonMapper);

    @Test
    void recMntList_행을_SeoulJobRow로_변환() {
        given(client.fetchAll("recMntList")).willReturn(new SeoulOpenApiPage(1, List.of(node("""
                {"COMPANY":"회사","TITLE":"제목","REG_DT":"26-09-14","JOBS_CD":"817100","UNKNOWN":"x"}
                """))));

        SeoulJobFetchResult result = fetcher.fetch();

        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.company()).isEqualTo("회사");
            assertThat(row.title()).isEqualTo("제목");
            assertThat(row.jobsCd()).isEqualTo("817100");
        });
        assertThat(result.complete()).isTrue();
    }

    @Test
    void 전체보다_적게_받으면_complete_false() {
        given(client.fetchAll("recMntList")).willReturn(new SeoulOpenApiPage(2, List.of(node("""
                {"COMPANY":"회사","TITLE":"제목"}
                """))));

        assertThat(fetcher.fetch().complete()).isFalse();
    }

    @Test
    void 읽지_못한_행은_externalId만_계산해_따로_반환() {
        given(client.fetchAll("recMntList")).willReturn(new SeoulOpenApiPage(1, List.of(node("""
                {"COMPANY":"회사","TITLE":"제목","REG_DT":"26-09-14","WORK_REGION":"서울","JOB_CONT":{"nested":true}}
                """))));

        SeoulJobFetchResult result = fetcher.fetch();

        assertThat(result.rows()).isEmpty();
        assertThat(result.unreadableExternalIds())
                .containsExactly(SeoulJobPostingMapper.externalId("회사", "제목", "26-09-14", "서울"));
    }

    @Test
    void 호출_실패는_EXTERNAL_API_FAILED() {
        given(client.fetchAll("recMntList")).willThrow(new SeoulOpenApiException("실패"));

        assertThatThrownBy(fetcher::fetch)
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(JobPostingErrorCode.EXTERNAL_API_FAILED);
    }

    private JsonNode node(String json) {
        return jsonMapper.readTree(json);
    }
}
