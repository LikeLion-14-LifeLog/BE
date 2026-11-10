package com.lifelog.domain.major.controller;

import com.lifelog.domain.major.dto.MajorInitResDto;
import com.lifelog.domain.major.dto.MajorListResDto;
import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.global.response.SliceResponse;
import com.lifelog.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Major", description = "전공 API")
@SecurityRequirement(name = "bearerAuth")
public interface MajorControllerDocs {

    @Operation(summary = "전공 목록 조회 및 검색", description = """
            ### 1. API 역할
            학과명을 대소문자 구분 없이 부분 일치 검색하고 대계열로 필터링합니다.
            학과코드 오름차순이며 이름이 같아도 코드가 다르면 각각 반환합니다.
            ### 2. Request Header
            Authorization: Bearer {accessToken}. 로그인한 사용자 인증이 필요합니다.
            ### 3. Path Variable
            없음
            ### 4. Query String
            keyword: 선택. 앞뒤 공백 제거 후 최대 100자. 누락·공백이면 전체 조회.
            mainField: 선택. 대계열 enum. 생략하면 전체 계열.
            page: 선택. 기본값 1, 최솟값 1.
            size: 선택. 기본값 20, 1~100.
            ### 5. Request Body
            없음
            ### 6. Response Header
            Content-Type: application/json
            ### 7. Response Body
            success: true, code: COMMON_200, message: 요청이 성공적으로 처리되었습니다.
            data.majors: majorId, mainField(enum), middleField(한글명), subField(한글명),
            name, code, degreeProcessName(enum)을 포함한 배열.
            data.page: 1 기반 페이지, size: 요청 페이지 크기, totalElements: 전체 건수,
            totalPages: 전체 페이지 수, hasNext: 다음 페이지 여부.
            결과가 없으면 majors: [], totalElements: 0, totalPages: 0, hasNext: false.
            범위를 넘는 페이지는 빈 배열과 실제 전체 집계를 반환합니다.
            오류 응답은 공통 success/code/message/data 형식을 사용합니다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "전공 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 정보 없음 또는 유효하지 않은 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 검색 조건",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @GetMapping(value = "/api/majors", produces = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<SliceResponse<MajorListResDto>> searchMajors(
            @Parameter(description = "학과명 검색어. 앞뒤 공백 제거 후 최대 100자")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "대계열. 생략하면 전체 계열")
            @RequestParam(value = "mainField", required = false) MajorMainField mainField,
            @Parameter(description = "1부터 시작하는 페이지 번호", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "페이지 크기 (1~100)", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size);

    @Operation(summary = "[관리자] 전공 데이터 초기화", description = """
            ### 1. API 역할
            서버의 data/major.csv를 검증한 뒤 code 기준으로 전공을 등록·갱신합니다.
            동일 코드·동일 내용의 중복 행은 제외하며, 내용이 충돌하면 전체 실패합니다.
            기존 majorId와 학력 참조 관계를 유지하고 원본에 없는 전공은 삭제하지 않습니다.
            ### 2. Request Header
            Authorization: Bearer {accessToken}. ADMIN 권한이 필요합니다.
            ### 3. Path Variable
            없음
            ### 4. Query String
            없음
            ### 5. Request Body
            없음. 파일이나 파일 경로를 전달하지 않습니다.
            ### 6. Response Header
            Content-Type: application/json
            ### 7. Response Body
            success: true, code: COMMON_200, message: 전공 데이터 초기화 성공, data: 집계 결과.
            sourceRowCount: 헤더·빈 줄을 제외한 원본 데이터 행 수.
            duplicateRowCount: 동일 코드·동일 내용으로 제외한 중복 행 수.
            totalCount: 중복 제거 후 처리 대상 수, createdCount: 신규 등록 수.
            updatedCount: 실제 값이 변경된 수, unchangedCount: 변경하지 않은 수.
            오류 응답은 공통 success/code/message/data 형식을 사용합니다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "전공 데이터 초기화 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 정보 없음 또는 유효하지 않은 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "원본 읽기·검증 또는 DB 저장 실패",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PutMapping("/api/admin/majors/init")
    ApiResponse<MajorInitResDto> initMajors();
}
