package com.lifelog.domain.jobposting.client;

import com.lifelog.domain.jobposting.exception.JobPostingErrorCode;
import com.lifelog.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SeoulJobApiClientTest {

    private static final String BASE = "http://seoul.test";

    private MockRestServiceServer server;
    private SeoulJobApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SeoulJobApiClient(builder.build(), "KEY", 2);
    }

    @Test
    void 전체_건수만큼_페이지를_나눠_조회() {
        expect(1, 2, page(3, "A", "B"));
        expect(3, 4, page(3, "C"));

        List<SeoulJobRow> rows = client.fetchAll();

        assertThat(rows).extracting(SeoulJobRow::title).containsExactly("A", "B", "C");
        assertThat(rows.getFirst().company()).isEqualTo("회사");
        server.verify();
    }

    @Test
    void 데이터가_없으면_빈_목록() {
        expect(1, 2, """
                {"RESULT":{"CODE":"INFO-200","MESSAGE":"해당하는 데이터가 없습니다."}}
                """);

        assertThat(client.fetchAll()).isEmpty();
    }

    @Test
    void 오류_코드면_EXTERNAL_API_FAILED() {
        expect(1, 2, """
                {"RESULT":{"CODE":"ERROR-500","MESSAGE":"서버 오류입니다."}}
                """);

        assertFailed();
    }

    @Test
    void HTTP_오류면_EXTERNAL_API_FAILED() {
        server.expect(requestTo(BASE + "/KEY/json/recMntList/1/2/")).andRespond(withServerError());

        assertFailed();
    }

    @Test
    void 두번째_페이지가_실패해도_EXTERNAL_API_FAILED() {
        expect(1, 2, page(3, "A", "B"));
        server.expect(requestTo(BASE + "/KEY/json/recMntList/3/4/")).andRespond(withServerError());

        assertFailed();
    }

    @Test
    void 인증키가_없으면_호출하지_않고_실패() {
        SeoulJobApiClient noKey = new SeoulJobApiClient(RestClient.create(), "", 2);

        assertThatThrownBy(noKey::fetchAll)
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(JobPostingErrorCode.EXTERNAL_API_FAILED);
    }

    private void assertFailed() {
        assertThatThrownBy(client::fetchAll)
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(JobPostingErrorCode.EXTERNAL_API_FAILED);
    }

    private void expect(int start, int end, String body) {
        server.expect(requestTo(BASE + "/KEY/json/recMntList/" + start + "/" + end + "/"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static String page(int total, String... titles) {
        StringBuilder rows = new StringBuilder();
        for (String title : titles) {
            if (!rows.isEmpty()) {
                rows.append(',');
            }
            rows.append("{\"COMPANY\":\"회사\",\"TITLE\":\"").append(title).append("\",\"UNKNOWN_FIELD\":\"x\"}");
        }
        return "{\"recMntList\":{\"list_total_count\":" + total
                + ",\"RESULT\":{\"CODE\":\"INFO-000\",\"MESSAGE\":\"정상 처리되었습니다\"},\"row\":[" + rows + "]}}";
    }
}
