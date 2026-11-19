package com.lifelog.domain.employeecard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "사원증 생성 요청 (multipart/form-data)")
public record EmployeeCardCreateRequest(
        @Schema(description = "얼굴 사진 (JPG·PNG·WEBP, 10MB 이하). 캐릭터 생성에만 쓰고 저장하지 않습니다.",
                type = "string", format = "binary")
        @NotNull(message = "사진을 올려주세요.")
        MultipartFile photo,

        @Schema(description = "이름", example = "홍길동")
        @NotBlank(message = "이름을 입력해주세요.")
        @Size(max = 20, message = "이름은 20자 이하로 입력해주세요.")
        String name,

        @Schema(description = "학교", example = "한국대학교")
        @NotBlank(message = "학교를 입력해주세요.")
        @Size(max = 50, message = "학교는 50자 이하로 입력해주세요.")
        String school,

        @Schema(description = "학과", example = "컴퓨터공학과")
        @NotBlank(message = "학과를 입력해주세요.")
        @Size(max = 50, message = "학과는 50자 이하로 입력해주세요.")
        String department,

        @Schema(description = "외형 보조 설명 (선택). 사진만으로 부족한 특징을 적습니다.", example = "검은 단발머리, 둥근 안경")
        @Size(max = 200, message = "외형 설명은 200자 이하로 입력해주세요.")
        String appearance
) {
}
