package com.lifelog.domain.employeecard.controller;

import com.lifelog.domain.employeecard.dto.EmployeeCardCreateRequest;
import com.lifelog.domain.employeecard.service.EmployeeCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "EmployeeCard", description = "사원증 API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EmployeeCardController {

    private final EmployeeCardService employeeCardService;

    @Operation(
            summary = "사원증 캐릭터 이미지 생성",
            description = "얼굴 사진과 성별로 AI 3D 캐릭터 이미지를 만들어 PNG 파일로 반환합니다. "
                    + "사원증 디자인은 프론트에서 그립니다. "
                    + "사진과 결과 이미지는 서버에 저장하지 않습니다. 생성에 수십 초가 걸릴 수 있습니다. "
                    + "로그인 없이 사용할 수 있습니다. 실패하면 JSON 에러 응답을 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "생성 성공 (PNG 이미지)",
                    content = @Content(mediaType = MediaType.IMAGE_PNG_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "성별 누락·잘못된 값 (COMMON_400) 또는 사진 형식·크기 오류 (EMPLOYEE_CARD_400)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "업로드 크기 초과 (COMMON_413)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "동시 생성 요청 초과 (EMPLOYEE_CARD_429) 또는 OpenAI 요청량 제한"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "OpenAI 호출 실패 또는 부적절한 이미지로 거절"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "504", description = "OpenAI 응답 시간 초과")
    })
    @PostMapping(value = "/employee-cards", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> create(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @ModelAttribute EmployeeCardCreateRequest request
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"employee-card.png\"")
                .body(employeeCardService.createCharacterImage(memberId, request));
    }
}
