package com.lifelog.domain.certification.controller;
import com.lifelog.domain.certification.dto.CertificationInitResDto;
import com.lifelog.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.PutMapping;
@Tag(name = "Certification", description = "자격증 API")
public interface CertificationControllerDocs {
    @Operation(summary = "[관리자] 자격증 데이터 초기화", description = """
            ### 1. API 역할
            서버의 data/certification.csv로 등록·갱신하며 기존 PK와 참조를 유지합니다.
            코드가 없으면 유형·이름으로 식별합니다. 원본 밖 기존 행은 삭제하지 않습니다.
            ### 2. Request Header
            현재 인증 미적용. 추후 Bearer 토큰과 관리자 권한을 연결합니다.
            ### 3. Path Variable
            없음
            ### 4. Query String
            없음
            ### 5. Request Body
            없음. 파일 업로드나 파일 경로를 받지 않습니다.
            ### 6. Response Header
            Content-Type: application/json
            ### 7. Response Body
            success: true,
            code: COMMON_200,
            message: [관리자] 자격증 데이터 초기화 성공
            data.totalCount: 원본 행 수,
            createdCount: 신규 등록 수,
            updatedCount: 실제 변경 수,
            unchangedCount: 동일 데이터 수.
            식별 충돌은 409 CONFLICT, 파일·저장 실패는 500 INTERNAL_SERVER_ERROR입니다.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "초기화 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "식별 충돌"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "파일·저장 실패")
    })
    @PutMapping("/api/admin/certifications/init")
    ApiResponse<CertificationInitResDto> initCertifications();
}
