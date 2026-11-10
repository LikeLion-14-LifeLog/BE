package com.lifelog.domain.residentialarea.controller;

import com.lifelog.domain.residentialarea.dto.ResidentialAreaListResDto;
import com.lifelog.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "Residential Area", description = "거주 지역 API")
@SecurityRequirement(name = "bearerAuth")
public interface ResidentialAreaControllerDocs {

    @Operation(summary = "거주 지역 목록 조회", description = """
            ### 1. API 역할
            거주 지역 선택에 사용하는 전체 17개 지역을 ENUM 선언 순서대로 반환합니다.
            ### 2. Request Header
            Authorization: Bearer {accessToken}. 로그인한 사용자 인증이 필요합니다.
            ### 3. Path Variable
            없음
            ### 4. Query String
            없음
            ### 5. Request Body
            없음
            ### 6. Response Header
            Content-Type: application/json
            ### 7. Response Body
            success: true, code: COMMON_200, message: 요청이 성공적으로 처리되었습니다.
            data.residentialAreas: 전체 지역 목록.
            code: 지역 ENUM 코드(예: SEOUL), name: 화면 표시용 지역명(예: 서울).
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "거주 지역 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 정보 없음 또는 유효하지 않은 토큰")
    })
    @GetMapping(value = "/api/residential-areas", produces = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<ResidentialAreaListResDto> getResidentialAreas();
}
