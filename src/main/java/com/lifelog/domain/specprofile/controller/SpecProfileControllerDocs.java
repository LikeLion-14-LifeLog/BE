package com.lifelog.domain.specprofile.controller;

import com.lifelog.domain.specprofile.dto.SpecProfileTemporarySaveReqDto;
import com.lifelog.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "SpecProfile", description = "회원 스펙 API")
@SecurityRequirement(name = "bearerAuth")
public interface SpecProfileControllerDocs {
    @Operation(summary = "회원 스펙 임시 저장", description = """
            ### 1. API 역할
            - 본인 CURRENT 프로필과 회원의 희망 직무를 저장합니다. 연봉 진단은 실행하지 않습니다.
            - 하위 목록은 PK가 있으면 덮어쓰기, 없으면 생성, 요청 PK에 없는 기존 행은 삭제합니다.
            ### 2. Request Header
            - Authorization: Bearer {accessToken}. Content-Type: application/json.
            ### 3. Path Variable
            - 없음
            ### 4. Query String
            - 없음
            ### 5. Request Body
            - birthYear, residenceRegion, jobId는 필수입니다. 출생년도는 미래일 수 없습니다.
            - academicAbilities는 null 또는 빈 배열을 허용하며 기존 학력을 모두 삭제합니다.
            - certificates, languageExamScores, experiences는 필수 배열이며 없으면 []를 전달합니다.
            - 기존 행은 academicAbilityId, specProfileCertificateId, languageExamScoreId, experienceId로 식별합니다.
            - PK는 본인 CURRENT 소유여야 하며 중복 PK는 허용하지 않습니다.
            - majorId는 숫자이며 전공계열과 일치해야 합니다. 연월은 YYYY-MM 형식입니다.
            - NUMERIC 시험은 숫자만, GRADE 시험은 등급만, NUMERIC_AND_GRADE 시험은 둘 다 필수입니다.
            - 점수 범위 및 허용 등급 목록은 검증하지 않습니다.
            - 어학 데이터가 비어 있으면 '어학 데이터 초기화가 필요합니다' 오류를 반환합니다.
            - 활동명은 공백 불가 및 최대 255자, 설명은 최대 300자입니다.
            - 수상 시 주관 기관명과 수상 등급은 필수이며 미수상 시 null입니다.
            ### 6. Response Header
            - Content-Type: application/json
            ### 7. Response Body
            - success: true, code: COMMON_201, message: 회원 스펙 임시 저장 성공, data: null.
            - 오류 응답은 기존 success/code/message/data 형식을 사용합니다.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "임시 저장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청값 또는 점수유형 입력 규칙 위반"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "프로필, 참조 데이터 또는 본인 하위 항목 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "어학 데이터 미초기화 또는 프로필 무결성 오류")
    })
    @PutMapping(value = "/api/spec-profiles/current/temporary", consumes = "application/json", produces = "application/json")
    ResponseEntity<ApiResponse<Void>> saveTemporary(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody SpecProfileTemporarySaveReqDto request);
}

