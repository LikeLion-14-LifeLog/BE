package com.lifelog.domain.major.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import java.util.List;

@Builder
@Schema(description = "현재 페이지의 전공 목록")
public record MajorListResDto(List<MajorResDto> majors) {
    public static MajorListResDto create(List<MajorResDto> majors) {
        return MajorListResDto.builder().majors(majors).build();
    }
}
