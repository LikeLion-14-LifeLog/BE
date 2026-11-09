package com.lifelog.global.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SliceResponseTest {
    record Items(List<String> items) {
    }

    @Test
    @DisplayName("목록 DTO를 펼치고 1 기반 페이지와 전체 집계를 직렬화한다")
    void 페이지_직렬화_성공() throws Exception {
        // given
        var page = new PageImpl<>(List.of("A", "B"), PageRequest.of(1, 2), 5);
        // when
        var json = new JsonMapper().valueToTree(SliceResponse.of(page, new Items(page.getContent())));
        // then
        assertThat(json.has("content")).isFalse();
        assertThat(json.get("items").size()).isEqualTo(2);
        assertThat(json.get("page").asInt()).isEqualTo(2);
        assertThat(json.get("size").asInt()).isEqualTo(2);
        assertThat(json.get("totalElements").asLong()).isEqualTo(5);
        assertThat(json.get("totalPages").asInt()).isEqualTo(3);
        assertThat(json.get("hasNext").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("빈 결과와 마지막 페이지에서도 요청한 크기를 유지한다")
    void 빈결과_마지막페이지_성공() {
        // given / when
        var empty = SliceResponse.of(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0), new Items(List.of()));
        var last = SliceResponse.of(new PageImpl<>(List.of("A"), PageRequest.of(1, 2), 3), new Items(List.of("A")));
        // then
        assertThat(empty.page()).isEqualTo(1);
        assertThat(empty.size()).isEqualTo(20);
        assertThat(empty.totalElements()).isZero();
        assertThat(empty.totalPages()).isZero();
        assertThat(empty.hasNext()).isFalse();
        assertThat(last.size()).isEqualTo(2);
        assertThat(last.hasNext()).isFalse();
    }
}
