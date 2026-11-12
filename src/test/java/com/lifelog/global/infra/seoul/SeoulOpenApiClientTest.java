package com.lifelog.global.infra.seoul;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SeoulOpenApiClientTest {

    private static final String BASE = "http://seoul.test";
    private static final String SERVICE = "testService";

    private MockRestServiceServer server;
    private SeoulOpenApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SeoulOpenApiClient(builder.build(), "KEY", 2);
    }

    @Test
    void 전체_건수만큼_페이지를_나눠_조회() {
        expect(1, 2, page(3, "A", "B"));
        expect(3, 4, page(3, "C"));

        SeoulOpenApiPage result = client.fetchAll(SERVICE);

        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.rows()).extracting(node -> node.get("NAME").asString()).containsExactly("A", "B", "C");
        assertThat(result.isComplete()).isTrue();
        server.verify();
    }

    @Test
    void 데이터가_없으면_빈_결과() {
        expect(1, 2, """
                {"RESULT":{"CODE":"INFO-200","MESSAGE":"해당하는 데이터가 없습니다."}}
                """);

        SeoulOpenApiPage result = client.fetchAll(SERVICE);

        assertThat(result.rows()).isEmpty();
        assertThat(result.totalCount()).isZero();
    }

    @Test
    void 중간_페이지가_비어_있으면_받은_데이터까지_반환하고_미완료() {
        expect(1, 2, page(3, "A", "B"));
        expect(3, 4, """
                {"RESULT":{"CODE":"INFO-200","MESSAGE":"해당하는 데이터가 없습니다."}}
                """);

        SeoulOpenApiPage result = client.fetchAll(SERVICE);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.isComplete()).isFalse();
    }

    @Test
    void 받은_건수가_전체보다_적으면_미완료() {
        expect(1, 2, page(3, "A"));
        expect(3, 4, page(3, "C"));

        SeoulOpenApiPage result = client.fetchAll(SERVICE);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.isComplete()).isFalse();
    }

    @Test
    void 오류_코드면_예외() {
        expect(1, 2, """
                {"RESULT":{"CODE":"ERROR-500","MESSAGE":"서버 오류입니다."}}
                """);

        assertThatThrownBy(() -> client.fetchAll(SERVICE)).isInstanceOf(SeoulOpenApiException.class);
    }

    @Test
    void HTTP_오류면_예외() {
        server.expect(requestTo(url(1, 2))).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchAll(SERVICE)).isInstanceOf(SeoulOpenApiException.class);
    }

    @Test
    void 두번째_페이지_호출이_실패해도_예외() {
        expect(1, 2, page(3, "A", "B"));
        server.expect(requestTo(url(3, 4))).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchAll(SERVICE)).isInstanceOf(SeoulOpenApiException.class);
    }

    @Test
    void 인증키가_없으면_호출하지_않고_예외() {
        SeoulOpenApiClient noKey = new SeoulOpenApiClient(RestClient.create(), "", 2);

        assertThatThrownBy(() -> noKey.fetchAll(SERVICE)).isInstanceOf(SeoulOpenApiException.class);
    }

    @Test
    void 한_번에_1000건을_넘는_범위는_거부() {
        assertThatThrownBy(() -> client.fetchRange(SERVICE, 1, 1001)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 행은_JsonNode로_그대로_반환() {
        expect(1, 2, page(1, "A"));

        JsonNode row = client.fetchRange(SERVICE, 1, 2).rows().getFirst();

        assertThat(row.get("EXTRA").asString()).isEqualTo("x");
    }

    private void expect(int start, int end, String body) {
        server.expect(requestTo(url(start, end))).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static String url(int start, int end) {
        return BASE + "/KEY/json/" + SERVICE + "/" + start + "/" + end + "/";
    }

    private static String page(int total, String... names) {
        StringBuilder rows = new StringBuilder();
        for (String name : names) {
            if (!rows.isEmpty()) {
                rows.append(',');
            }
            rows.append("{\"NAME\":\"").append(name).append("\",\"EXTRA\":\"x\"}");
        }
        return "{\"" + SERVICE + "\":{\"list_total_count\":" + total
                + ",\"RESULT\":{\"CODE\":\"INFO-000\",\"MESSAGE\":\"정상 처리되었습니다\"},\"row\":[" + rows + "]}}";
    }
}
