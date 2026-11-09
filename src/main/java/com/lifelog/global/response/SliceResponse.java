package com.lifelog.global.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import org.springframework.data.domain.Page;

@Builder
@Schema(description = "목록과 전체 건수를 포함한 페이지 응답")
public record SliceResponse<T>(
        @JsonUnwrapped T content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    /**
     * 페이지 메타데이터와 목록 DTO를 결합한다.
     * @param page 전체 건수를 포함한 조회 결과
     * @param content 펼칠 객체 DTO. 컬렉션 자체 및 페이징 필드와 중복된 필드명은 지원하지 않는다.
     * @return 1 기반 페이지 응답
     * @param <T> 도메인 목록 DTO 타입
     */
    public static <T> SliceResponse<T> of(Page<?> page, T content) {
        return SliceResponse.<T>builder().content(content).page(page.getNumber() + 1).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages()).hasNext(page.hasNext()).build();
    }
}
