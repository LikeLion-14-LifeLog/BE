package com.lifelog.domain.major.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MajorOpenApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper mapper;

    @Test
    @DisplayName("OpenAPI의 전공 응답 스키마도 펼쳐진 목록과 전체 집계를 포함한다")
    void 전공_응답스키마_성공() throws Exception {
        // given / when
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode document = mapper.readTree(body);
        JsonNode schema = document.at("/paths/~1api~1majors/get/responses/200/content/application~1json/schema");
        JsonNode response = resolve(document, schema);
        JsonNode data = resolve(document, response.path("properties").path("data"));
        // then
        assertThat(data.path("properties").has("majors")).isTrue();
        assertThat(data.path("properties").has("content")).isFalse();
        for (String field : new String[]{"page", "size", "totalElements", "totalPages", "hasNext"}) {
            assertThat(data.path("properties").has(field)).as(field).isTrue();
        }
        assertThat(document.at("/paths/~1api~1admin~1majors~1init/put").isMissingNode()).isFalse();
    }

    private JsonNode resolve(JsonNode document, JsonNode schema) {
        return schema.has("$ref") ? document.at(schema.get("$ref").asString().substring(1)) : schema;
    }
}
