package com.lifelog.domain.employeecard.dto;

import com.lifelog.domain.employeecard.enums.EmployeeCardGender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "사원증 캐릭터 생성 요청 (multipart/form-data)")
public record EmployeeCardCreateRequest(
        @Schema(description = "얼굴 사진 (JPG·PNG·WEBP, 10MB 이하). 캐릭터 생성에만 쓰고 저장하지 않습니다.",
                type = "string", format = "binary")
        @NotNull(message = "사진을 올려주세요.")
        MultipartFile photo,

        @Schema(description = "성별 (MALE: 남성, FEMALE: 여성)", example = "FEMALE")
        @NotNull(message = "성별을 선택해주세요.")
        EmployeeCardGender gender
) {
}
