package com.lifelog.domain.certification;

import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.certification.enums.CertificationMainCategory;
import com.lifelog.domain.certification.enums.CertificationType;
import com.lifelog.domain.certification.repository.CertificationRepository;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.global.security.JwtProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static com.lifelog.domain.certification.enums.CertificationMainCategory.COMMON;
import static com.lifelog.domain.certification.enums.CertificationMainCategory.INFORMATION_COMMUNICATIONS;
import static com.lifelog.domain.certification.enums.CertificationType.NATIONAL_TECHNICAL;
import static com.lifelog.domain.certification.enums.CertificationType.PRIVATE;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class CertificationSearchIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private CertificationRepository repository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JsonMapper mapper;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private Member authenticatedMember;
    private String accessToken;

    @BeforeAll
    void prepareAuthentication() {
        authenticatedMember = memberRepository.saveAndFlush(new Member("certification-search@example.com"));
        accessToken = jwtProvider.createAccessToken(authenticatedMember.getId());
    }

    @AfterAll
    void cleanAuthentication() {
        if (authenticatedMember != null) {
            memberRepository.deleteById(authenticatedMember.getId());
        }
    }

    @BeforeEach
    void prepareDatabase() {
        repository.deleteAllInBatch();
    }

    @AfterEach
    void cleanDatabase() {
        repository.deleteAllInBatch();
    }

    @Test
    @DisplayName("기본 요청은 이름 순으로 전체 목록과 기본 페이지 정보를 반환한다")
    void 기본조건_전체조회_성공() throws Exception {
        // given
        save("나자격", "B01", PRIVATE, COMMON, null);
        save("가자격", "A01", NATIONAL_TECHNICAL, INFORMATION_COMMUNICATIONS, "공인기관");
        // when
        ApiResult result = get("");
        // then
        JsonNode data = successData(result);
        assertThat(names(data)).containsExactly("가자격", "나자격");
        assertPage(data, 1, 20, 2, 1, false);
    }

    @Test
    @DisplayName("빈 문자열과 공백뿐인 검색어는 길이에 관계없이 전체 조회한다")
    void 공백검색어_전체조회_성공() throws Exception {
        // given
        save("자격", null, PRIVATE, COMMON, null);
        // when / then
        for (String keyword : List.of("", " \t\n", " ".repeat(101))) {
            JsonNode data = successData(get("keyword=" + encode(keyword)));
            assertThat(names(data)).containsExactly("자격");
            assertPage(data, 1, 20, 1, 1, false);
        }
    }

    @Test
    @DisplayName("검색어의 앞뒤 공백만 제거하고 내부 공백은 보존한다")
    void 검색어_앞뒤공백제거_성공() throws Exception {
        // given
        save("정보 처리 기사", null, NATIONAL_TECHNICAL, INFORMATION_COMMUNICATIONS, null);
        save("정보처리기사", null, NATIONAL_TECHNICAL, INFORMATION_COMMUNICATIONS, null);
        // when
        JsonNode data = successData(get("keyword=" + encode(" \t정보 처리 \n")));
        // then
        assertThat(names(data)).containsExactly("정보 처리 기사");
    }

    @Test
    @DisplayName("영문 검색어는 대소문자를 구분하지 않고 부분 검색한다")
    void 영문_대소문자무시_부분검색_성공() throws Exception {
        // given
        save("Computer Expert", null, PRIVATE, COMMON, null);
        save("Other", null, PRIVATE, COMMON, null);
        // when
        JsonNode data = successData(get("keyword=pUtEr"));
        // then
        assertThat(names(data)).containsExactly("Computer Expert");
    }

    @Test
    @DisplayName("검색어 유형 대분류의 여덟 조건 조합을 AND로 적용한다")
    void 선택조건_조합검색_성공() throws Exception {
        // given
        save("정보A", null, PRIVATE, COMMON, null);
        save("정보B", null, PRIVATE, INFORMATION_COMMUNICATIONS, null);
        save("정보C", null, NATIONAL_TECHNICAL, COMMON, null);
        save("정보D", null, NATIONAL_TECHNICAL, INFORMATION_COMMUNICATIONS, null);
        save("기타A", null, PRIVATE, COMMON, null);
        save("기타B", null, PRIVATE, INFORMATION_COMMUNICATIONS, null);
        save("기타C", null, NATIONAL_TECHNICAL, COMMON, null);
        save("기타D", null, NATIONAL_TECHNICAL, INFORMATION_COMMUNICATIONS, null);
        String keyword = "keyword=" + encode("정보");
        List<String> queries = List.of("", keyword, "type=PRIVATE", "mainCategory=COMMON",
                keyword + "&type=PRIVATE", keyword + "&mainCategory=COMMON",
                "type=PRIVATE&mainCategory=COMMON", keyword + "&type=PRIVATE&mainCategory=COMMON");
        List<List<String>> expected = List.of(
                List.of("기타A", "기타B", "기타C", "기타D", "정보A", "정보B", "정보C", "정보D"),
                List.of("정보A", "정보B", "정보C", "정보D"),
                List.of("기타A", "기타B", "정보A", "정보B"),
                List.of("기타A", "기타C", "정보A", "정보C"),
                List.of("정보A", "정보B"), List.of("정보A", "정보C"),
                List.of("기타A", "정보A"), List.of("정보A"));
        // when / then
        for (int i = 0; i < queries.size(); i++) {
            JsonNode data = successData(get(queries.get(i)));
            assertThat(names(data)).as(queries.get(i)).containsExactlyElementsOf(expected.get(i));
            assertThat(data.path("totalElements").asLong()).isEqualTo(expected.get(i).size());
        }
    }

    @Test
    @DisplayName("같은 이름은 ID 오름차순으로 페이지를 나누며 서로 다른 행을 보존한다")
    void 동일명칭_ID오름차순_성공() throws Exception {
        // given
        Certification first = save("동일자격", "A01", PRIVATE, COMMON, null);
        Certification second = save("동일자격", "A02", PRIVATE, COMMON, null);
        // when
        JsonNode page1 = successData(get("size=1"));
        JsonNode page2 = successData(get("page=2&size=1"));
        // then
        assertThat(page1.path("certifications").get(0).path("certificationId").asLong()).isEqualTo(first.getId());
        assertThat(page2.path("certifications").get(0).path("certificationId").asLong()).isEqualTo(second.getId());
        assertPage(page1, 1, 1, 2, 2, true);
        assertPage(page2, 2, 1, 2, 2, false);
    }

    @Test
    @DisplayName("원본 필드와 한글 대분류명 및 PRIVATE 유형을 JSON으로 반환한다")
    void 응답필드_매핑_성공() throws Exception {
        // given
        Certification saved = save("무역영어 1급", "A01011-30", PRIVATE, COMMON, "대한상공회의소");
        // when
        JsonNode item = successData(get("type=PRIVATE")).path("certifications").get(0);
        // then
        assertThat(item.size()).isEqualTo(6);
        assertThat(item.path("certificationId").asLong()).isEqualTo(saved.getId());
        assertThat(item.path("name").asString()).isEqualTo("무역영어 1급");
        assertThat(item.path("code").asString()).isEqualTo("A01011-30");
        assertThat(item.path("type").asString()).isEqualTo("PRIVATE");
        assertThat(item.path("mainCategoryName").asString()).isEqualTo("공통");
        assertThat(item.path("authorizedInstitutionName").asString()).isEqualTo("대한상공회의소");
    }

    @Test
    @DisplayName("코드와 공인기관이 없는 자격증은 해당 필드를 null로 반환한다")
    void 선택필드_null반환_성공() throws Exception {
        // given
        save("정보처리기사", null, NATIONAL_TECHNICAL, INFORMATION_COMMUNICATIONS, null);
        // when
        JsonNode item = successData(get("")).path("certifications").get(0);
        // then
        assertThat(item.has("code")).isTrue();
        assertThat(item.path("code").isNull()).isTrue();
        assertThat(item.has("authorizedInstitutionName")).isTrue();
        assertThat(item.path("authorizedInstitutionName").isNull()).isTrue();
        assertThat(item.path("mainCategoryName").asString()).isEqualTo("정보통신");
        assertThat(item.path("type").asString()).isEqualTo("NATIONAL_TECHNICAL");
    }

    @Test
    @DisplayName("모든 기존 자격증 유형을 요청과 응답에 그대로 사용한다")
    void 전체유형_조회_성공() throws Exception {
        // given
        for (CertificationType type : CertificationType.values()) {
            save(type.name(), null, type, COMMON, null);
        }
        // when / then
        for (CertificationType type : CertificationType.values()) {
            JsonNode data = successData(get("type=" + type.name()));
            assertThat(data.path("certifications").size()).isEqualTo(1);
            assertThat(data.path("certifications").get(0).path("type").asString()).isEqualTo(type.name());
        }
    }

    @Test
    @DisplayName("퍼센트 밑줄 역슬래시는 와일드카드가 아닌 문자로 검색한다")
    void LIKE_특수문자_검색_성공() throws Exception {
        // given
        save("100%_\\자격", null, PRIVATE, COMMON, null);
        save("100ABC자격", null, PRIVATE, COMMON, null);
        // when / then
        for (String keyword : List.of("%", "_", "\\", "%_\\")) {
            JsonNode data = successData(get("keyword=" + encode(keyword)));
            assertThat(names(data)).containsExactly("100%_\\자격");
        }
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 배열과 0 집계를 반환한다")
    void 검색결과없음_성공() throws Exception {
        // given
        save("자격", null, PRIVATE, COMMON, null);
        // when
        JsonNode data = successData(get("keyword=" + encode("없음")));
        // then
        assertThat(names(data)).isEmpty();
        assertPage(data, 1, 20, 0, 0, false);
    }

    @Test
    @DisplayName("빈 데이터베이스에서도 요청한 페이지와 빈 목록을 반환한다")
    void 빈DB_전체조회_성공() throws Exception {
        // given / when
        JsonNode data = successData(get("page=3"));
        // then
        assertThat(names(data)).isEmpty();
        assertPage(data, 3, 20, 0, 0, false);
    }

    @Test
    @DisplayName("마지막 페이지를 초과해도 실제 전체 건수와 페이지 수를 유지한다")
    void 마지막페이지초과_성공() throws Exception {
        // given
        save("자격1", null, PRIVATE, COMMON, null);
        save("자격2", null, PRIVATE, COMMON, null);
        // when
        JsonNode data = successData(get("page=3&size=1"));
        // then
        assertThat(names(data)).isEmpty();
        assertPage(data, 3, 1, 2, 2, false);
    }

    @Test
    @DisplayName("검색어 길이는 앞뒤 공백 제거 후 100자까지 허용한다")
    void 검색어_길이경계_검증() throws Exception {
        // given
        save("짧은자격", null, PRIVATE, COMMON, null);
        // when
        JsonNode data = successData(get("keyword=" + encode(" " + "가".repeat(100) + " ")));
        ApiResult invalid = get("keyword=" + encode("가".repeat(101)));
        // then
        assertThat(names(data)).isEmpty();
        assertError(invalid, "INVALID_REQUEST");
    }

    @Test
    @DisplayName("페이지 번호와 크기 오류는 정의된 공통 오류로 반환한다")
    void 페이지_범위오류_실패() throws Exception {
        // given / when / then
        for (String page : List.of("0", "-1")) {
            assertError(get("page=" + page), "COMMON_400_1");
        }
        for (String size : List.of("0", "-1", "101")) {
            assertError(get("size=" + size), "COMMON_400_2");
        }
        assertPage(successData(get("size=100")), 1, 100, 0, 0, false);
    }

    @Test
    @DisplayName("잘못된 enum과 숫자 형식은 공통 400 오류로 반환한다")
    void 파라미터_타입오류_실패() throws Exception {
        // given / when / then
        for (String query : List.of("type=INVALID", "type=NATIONAL_PRIVATE", "mainCategory=INVALID",
                "page=abc", "size=1.5", "page=2147483648")) {
            assertError(get(query), "COMMON_400");
        }
    }

    private Certification save(String name, String code, CertificationType type,
                               CertificationMainCategory category, String institution) {
        return repository.saveAndFlush(Certification.builder().name(name).code(code).type(type)
                .mainCategory(category).authorizedInstitutionName(institution).build());
    }

    private ApiResult get(String query) throws Exception {
        URI uri = URI.create("http://localhost:" + port + "/api/certifications" + (query.isEmpty() ? "" : "?" + query));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.headers().firstValue("content-type").orElse("")).startsWith("application/json");
        return new ApiResult(response.statusCode(), mapper.readTree(response.body()));
    }

    private JsonNode successData(ApiResult result) {
        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().path("success").asBoolean()).isTrue();
        assertThat(result.body().path("code").asString()).isEqualTo("COMMON_200");
        assertThat(result.body().path("message").asString()).isEqualTo("자격증 데이터 조회 성공");
        JsonNode data = result.body().path("data");
        assertThat(data.has("content")).isFalse();
        assertThat(data.path("certifications").isArray()).isTrue();
        return data;
    }

    private List<String> names(JsonNode data) {
        JsonNode certifications = data.path("certifications");
        return IntStream.range(0, certifications.size())
                .mapToObj(index -> certifications.get(index).path("name").asString()).toList();
    }

    private void assertPage(JsonNode data, int page, int size, long totalElements, int totalPages, boolean hasNext) {
        assertThat(data.path("page").asInt()).isEqualTo(page);
        assertThat(data.path("size").asInt()).isEqualTo(size);
        assertThat(data.path("totalElements").asLong()).isEqualTo(totalElements);
        assertThat(data.path("totalPages").asInt()).isEqualTo(totalPages);
        assertThat(data.path("hasNext").asBoolean()).isEqualTo(hasNext);
    }

    private void assertError(ApiResult result, String code) {
        assertThat(result.status()).isEqualTo(400);
        assertThat(result.body().path("success").asBoolean()).isFalse();
        assertThat(result.body().path("code").asString()).isEqualTo(code);
        assertThat(result.body().path("message").asString()).isNotBlank();
        assertThat(result.body().path("data").isNull()).isTrue();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record ApiResult(int status, JsonNode body) {}
}
