package com.lifelog.domain.certification.controller;
import com.lifelog.domain.certification.dto.CertificationInitResDto;
import com.lifelog.domain.certification.dto.CertificationListResDto;
import com.lifelog.domain.certification.enums.CertificationType;
import com.lifelog.domain.certification.enums.CertificationMainCategory;
import com.lifelog.global.response.SliceResponse;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.lifelog.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.PutMapping;
@Tag(name = "Certification", description = "자격증 API")
@SecurityRequirement(name = "bearerAuth")
public interface CertificationControllerDocs {
    @Operation(summary = "자격증 목록 조회 및 검색", description = """
            ### 1. API 역할
            자격증명 부분 일치 검색과 유형·대분류 필터를 AND로 적용합니다.
            영문 대소문자를 구분하지 않으며 이름 오름차순, 같은 이름은 ID 오름차순입니다.
            자격증 선택이나 사용자 보유 자격증 저장은 수행하지 않습니다.
            ### 2. Request Header
            Authorization: Bearer {accessToken}. 로그인한 사용자 인증이 필요합니다.
            ### 3. Path Variable
            없음
            ### 4. Query String
            keyword: 선택. 앞뒤 공백 제거 후 최대 100자. 누락·공백이면 전체 조회.
            type: 선택. NATIONAL_PROFESSIONAL, NATIONAL_TECHNICAL, INTERNATIONAL, PRIVATE.
            mainCategory: 선택. 자격증 대분류 enum.
            page: 선택. 기본값 1, 최솟값 1.
            size: 선택. 기본값 20, 1~100.
            ### 5. Request Body
            없음
            ### 6. Response Header
            Content-Type: application/json
            ### 7. Response Body
            success: true, code: COMMON_200, message: 자격증 데이터 조회 성공.
            data.certifications: certificationId, name, code(nullable), type(enum 문자열),
            mainCategoryName(한글명), authorizedInstitutionName(nullable)을 포함한 배열.
            data.page: 1 기반 페이지, size: 페이지 크기, totalElements: 전체 건수,
            totalPages: 전체 페이지 수, hasNext: 다음 페이지 여부.
            검색 결과가 없으면 빈 배열, totalElements: 0, totalPages: 0, hasNext: false.
            페이지 범위를 초과하면 빈 배열과 실제 전체 집계를 반환합니다.
            오류는 공통 success/code/message/data 형식입니다.
            파라미터 변환 실패: 400 COMMON_400, 페이지 번호 오류: 400 COMMON_400_1,
            페이지 크기 오류: 400 COMMON_400_2, 검색어 길이 초과: 400 INVALID_REQUEST,
            서버 오류: 500 COMMON_500.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "자격증 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 검색 조건"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 정보 없음 또는 유효하지 않은 토큰"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @GetMapping(value = "/api/certifications", produces = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<SliceResponse<CertificationListResDto>> searchCertifications(
            @Parameter(description = "자격증명 검색어. 앞뒤 공백 제거 후 최대 100자")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "자격증 유형")
            @RequestParam(value = "type", required = false) CertificationType type,
            @Parameter(description = "자격증 대분류")
            @RequestParam(value = "mainCategory", required = false) CertificationMainCategory mainCategory,
            @Parameter(description = "1부터 시작하는 페이지 번호", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "페이지 크기 (1~100)", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size);

    @Operation(summary = "[관리자] 자격증 데이터 초기화", description = """
            ### 1. API 역할
            서버의 data/certification.csv로 등록·갱신하며 기존 PK와 참조를 유지합니다.
            코드가 없으면 유형·이름으로 식별합니다. 원본 밖 기존 행은 삭제하지 않습니다.
            ### 2. Request Header
            Authorization: Bearer {accessToken}. ADMIN 권한이 필요합니다.
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
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 정보 없음 또는 유효하지 않은 토큰"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "식별 충돌"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "파일·저장 실패")
    })
    @PutMapping("/api/admin/certifications/init")
    ApiResponse<CertificationInitResDto> initCertifications();
}
