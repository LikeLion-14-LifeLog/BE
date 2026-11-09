package com.lifelog.domain.major.dto;

import com.lifelog.domain.major.enums.DegreeProcess;
import com.lifelog.domain.major.enums.MajorMainField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "전공 조회 결과")
public record MajorResDto(
        Long majorId,
        MajorMainField mainField,
        @Schema(example = "컴퓨터ㆍ통신") String middleField,
        @Schema(example = "전산학ㆍ컴퓨터공학") String subField,
        String name,
        String code,
        DegreeProcess degreeProcessName
) {
    public static MajorResDto create(Long majorId, MajorMainField mainField, String middleField,
                                     String subField, String name, String code, DegreeProcess degreeProcessName) {
        return MajorResDto.builder().majorId(majorId).mainField(mainField).middleField(middleField)
                .subField(subField).name(name).code(code).degreeProcessName(degreeProcessName).build();
    }
}
