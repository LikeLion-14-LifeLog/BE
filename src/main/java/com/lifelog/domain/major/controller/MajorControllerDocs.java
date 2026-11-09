package com.lifelog.domain.major.controller;

import com.lifelog.domain.major.dto.MajorInitResDto;
import com.lifelog.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Major", description = "전공 API")
@RequestMapping("/api/admin/majors")
public interface MajorControllerDocs {

    // TODO: 인증 API 도입 후 ADMIN 권한만 호출 가능하도록 제한한다.
    @Operation(summary = "[관리자] 전공 데이터 초기화", description = """
            ### 1. API 역할
            서버의 data/major.csv를 검증한 뒤 code 기준으로 전공을 등록·갱신합니다.
            동일 코드·동일 내용의 중복 행은 제외하며, 내용이 충돌하면 전체 실패합니다.
            기존 majorId와 학력 참조 관계를 유지하고 원본에 없는 전공은 삭제하지 않습니다.
            ### 2. Request Header
            현재 인증 헤더는 필요하지 않습니다. 인증 API 도입 후 관리자 권한을 적용합니다.
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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "원본 읽기·검증 또는 DB 저장 실패",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PutMapping("/init")
    ApiResponse<MajorInitResDto> initMajors();
}
