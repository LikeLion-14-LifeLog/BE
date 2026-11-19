package com.lifelog.domain.specprofile.controller;

import com.lifelog.domain.specprofile.dto.SpecProfileTemporarySaveReqDto;
import com.lifelog.domain.specprofile.dto.SpecProfileResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunResultResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunStopResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationReqDto;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileTargetSaveReqDto;
import com.lifelog.domain.specprofile.dto.SpecProfileTargetSaveResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileTargetResDto;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import org.springframework.web.bind.annotation.PatchMapping;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "SpecProfile", description = "회원 스펙 API")
@SecurityRequirement(name = "bearerAuth")
public interface SpecProfileControllerDocs {
    @Operation(summary = "저장한 목표 스펙 조회", description = """
            ### 1. API 역할
            - 로그인 회원의 type=TARGET, targetSpecSelected=true인 목표의 추가 조건만 조회합니다.
            - CURRENT 복제 항목은 제외하며 AI 분석·캐시 접근·DB 변경은 실행하지 않습니다.
            - 선택된 목표가 없으면 HTTP 200과 data:null을 반환합니다. false·null은 미선택입니다.
            - 기존 목표에 추가 항목 구분 정보가 없으면 TARGET_409_3으로 PUT 재저장을 안내합니다.
            ### 2. Request Header
            - Authorization: Bearer {accessToken}. Swagger Authorize에 로그인 토큰을 설정하세요.
            ### 3. Path Variable
            - 없음
            ### 4. Query String
            - 없음
            ### 5. Request Body
            - 없음
            ### 6. Response Header
            - Content-Type: application/json
            ### 7. Response Body
            - success:true, code:COMMON_200, message:요청 성공.
            - data: targetSpecProfileId, baseSpecProfileId, companyGroupScale(SMALL·LARGE·null), changes.
            - changes: academicAbilities, certificates, languageExamScores, experiences. 빈 목록은 항상 []입니다.
            - 항목 PK는 TARGET 하위 항목 PK이며 전공·자격증·시험의 마스터 ID와 이름도 함께 반환합니다.
            - 단일 미저장 값은 null, 날짜는 저장된 값만 yyyy-MM으로 반환합니다.
            - awarded=false이면 organizerName과 awardGrade는 null입니다. 예측 결과는 반환하지 않습니다.
            - 목표 재저장은 PUT /api/spec-profile/target을 사용합니다. 조회용 PK·이름을 제외하고 요청 필드를 제출하세요.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "선택한 목표 또는 미선택 상태", content = @Content(
                schema = @Schema(implementation = SpecProfileTargetResDto.class), examples = {
                    @ExampleObject(name = "선택된 목표", value = """
                            {"success":true,"code":"COMMON_200","message":"요청 성공","data":{
                              "targetSpecProfileId":11,"baseSpecProfileId":10,"companyGroupScale":"LARGE",
                              "changes":{"academicAbilities":[],"certificates":[],"languageExamScores":[],"experiences":[{
                                "experienceId":51,"type":"INTERNSHIP","title":"백엔드 개발 인턴","startedYearMonth":"2027-01",
                                "endedYearMonth":null,"description":"API 개발 및 서비스 운영 경험","awarded":false,
                                "organizerName":null,"awardGrade":null}]}}}
                            """),
                    @ExampleObject(name = "선택된 목표 없음", value = "{\"success\":true,\"code\":\"COMMON_200\",\"message\":\"요청 성공\",\"data\":null}")
                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "SPEC_PROFILE_404_1: 기준 CURRENT 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "TARGET_409_2: 선택 상태 오류 / TARGET_409_3: 기존 목표 재저장 필요", content = @Content(
                examples = @ExampleObject(value = "{\"success\":false,\"code\":\"TARGET_409_3\",\"message\":\"저장된 목표의 조회 정보가 없어요. 목표 스펙을 다시 저장해 주세요.\",\"data\":null}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "CURRENT 또는 전공 연결 무결성 오류")
    })
    @GetMapping(value = "/api/spec-profile/target", produces = "application/json")
    ApiResponse<SpecProfileTargetResDto> getSelectedTarget(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId);

    @Operation(summary = "목표 스펙 저장 & AI 추천 목표 스펙 생성", description = """
            ### 1. API 역할
            - 본인 CURRENT에 사용자 추가 조건을 적용하고 AI 대안 2개를 같은 기준으로 비교합니다.
            - DB에는 사용자 TARGET(type=TARGET, sort_order=1, 선택됨)과 해당 스펙만 저장·덮어씁니다.
            - 목표 기업 규모와 추가 항목 구분도 함께 저장하여 GET /api/spec-profile/target으로 다시 조회할 수 있습니다.
            - 시뮬레이션 결과·준비 정보·AI 대안·분석 실행 이력은 저장하지 않고 동기로 반환합니다.
            - 동일 조건의 최근 메모리 캐시가 있으면 사용자 분석을 재사용하고, 없으면 재분석합니다.
            ### 2. Request Header
            - Authorization: Bearer {accessToken}. Content-Type: application/json.
            - Swagger에서는 Authorize에 로그인 토큰을 설정한 뒤 GET /api/spec-profile로 본인의 data.specProfileId를 확인하세요.
            ### 3. Path Variable
            - 없음
            ### 4. Query String
            - 없음
            ### 5. Request Body
            - baseSpecProfileId: 본인 CURRENT PK. 필수 양의 정수(int64).
            - Swagger 기본 예시는 인턴 경험 추가입니다. baseSpecProfileId의 10만 본인 CURRENT ID로 바꾸면 별도 마스터 ID 없이 요청할 수 있습니다.
            - CURRENT의 출생연도·거주지·희망 직무·학력 등 분석 필수 스펙이 먼저 입력되어 있어야 합니다.
            - companyGroupScale: SMALL, LARGE 또는 null. 세 선택지에 동일하게 적용합니다.
            - changes: academicAbilities, certificates, languageExamScores, experiences 배열. 생략·null은 빈 목록입니다.
            - 추가 항목의 전공·자격증·시험은 실제 마스터 ID를 사용하며 현재 자격증·시험과 중복할 수 없습니다.
            - 날짜는 yyyy-MM이며 미래 월을 허용합니다. 졸업·취득·만료·종료월은 미정이면 null입니다.
            - 종료월은 시작월 이전일 수 없고, 만료월은 취득월 이전일 수 없습니다.
            - 졸업·취득·만료·종료월은 목표 스펙에 보존하지만 기존 시뮬레이션과 동일하게 분석에서는 제외합니다.
            - 수상 시 organizerName과 awardGrade가 필수이고 미수상 시 두 값은 null입니다.
            ### 6. Response Header
            - Content-Type: application/json
            ### 7. Response Body
            - success: true, code: COMMON_200, message: 목표 스펙이 저장되고 AI 추천 대안이 생성되었습니다.
            - data: baseSpecProfileId, companyGroupScale, currency(KRW), baseExpectedStartingSalary,
              selectedTargetSpecProfileId, options, salaryNotice, preparationNotice.
            - options는 slotNumber 1, 2, 3 순서입니다. 1은 USER_INPUT/selected=true, 2·3은 AI_RECOMMENDATION/selected=false입니다.
            - targetSpecProfileId: 사용자 슬롯만 실제 저장 ID이며 AI 대안은 필드 생략 없이 null입니다.
            - title, changes: 준비안 이름과 다시 제출할 수 있는 추가 스펙. 대안 선택 시 changes를 이 PUT에 제출합니다.
            - expectedStartingSalary: 각 선택지를 기존 시뮬레이터로 분석한 첫 취업 예상 연봉(원).
            - salaryIncreaseAmount: 각 예상 초봉에서 공통 CURRENT 예상 초봉을 뺀 값. 음수일 수 있습니다.
            - preparationPeriod, estimatedCost, decisionCriteria: 각 목표의 준비 기간·원화 비용·직무 활용도를 한 줄로 반환합니다.
              문장형 설명은 해요체이며 AI의 판단 과정·산정 가정·포함/제외 항목은 표시하지 않습니다.
            - AI 대안은 재분석 후 기간·비용·활용도·연봉을 종합 평가하며 연봉 상승만을 필수 조건으로 삼지 않습니다.
            - 생성·검증·분석에 실패하면 TARGET을 변경하지 않습니다. 분석 중 CURRENT 변경 시 409로 재요청을 안내합니다.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "사용자 목표 저장 및 비교 결과 생성", content = @Content(schema = @Schema(implementation = SpecProfileTargetSaveResDto.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력 조건·날짜·점수 오류 또는 사용자 목표 비교 표본 부족"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "본인 CURRENT 또는 참조 정보 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "분석 중 CURRENT 변경 또는 목표 슬롯 충돌"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "AI 요청량 제한"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "프롬프트 준비 또는 목표 저장 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "유효한 대안 생성·종합 평가 또는 AI 응답 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "AI 서비스 이용 불가"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "504", description = "AI 응답 시간 초과")
    })
    @PutMapping(value = "/api/spec-profile/target", consumes = "application/json", produces = "application/json")
    ApiResponse<SpecProfileTargetSaveResDto> saveTarget(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody SpecProfileTargetSaveReqDto request);

    @Operation(summary = "연봉 시뮬레이션 결과 요청", description = """
            ### 1. API 역할
            - 본인 CURRENT 스펙과 목표 조건을 추가한 가상 스펙의 첫 취업·3년 차·5년 차 연봉을 계산합니다.
            - 현재 항목은 유지하며 요청의 학력·자격증·어학·경험 목록을 각각 추가합니다.
            - 프로필, 분석 실행 이력, 이벤트, 결과는 DB에 저장하지 않습니다. 완료 결과를 동기로 반환합니다.
            - 전체 분석 성공 후 회원별 최신 스냅샷·기업 규모·현재/목표 분석을 메모리 캐시에 교체합니다.
            ### 2. Request Header
            - Authorization: Bearer {accessToken}. Content-Type: application/json.
            ### 3. Path Variable
            - 없음
            ### 4. Query String
            - 없음
            ### 5. Request Body
            - baseSpecProfileId: 본인 CURRENT 프로필 PK. 필수 양의 정수(int64).
            - companyGroupScale: SMALL 또는 LARGE. 선택값이며 생략·null이면 기업 규모를 지정하지 않습니다.
            - changes: 추가할 academicAbilities, certificates, languageExamScores, experiences 배열.
            - changes 또는 목록의 생략·null·빈 배열은 추가 조건 없음이며, 요청 간 조건을 누적하지 않습니다.
            - 기존 항목 PK를 받지 않습니다. 전공·자격증·어학 시험 PK는 등록된 마스터 데이터를 참조합니다.
            - 현재 스펙과 중복되는 자격증·시험 또는 요청 내 중복 자격증·시험은 거절합니다.
            - graduatedYearMonth, acquiredYearMonth, expiresYearMonth, endedYearMonth는 무시하며 검증·예측에 사용하지 않습니다.
            - 경험의 startedYearMonth는 yyyy-MM이며 미래 월을 허용합니다. 수상 시 기관명·수상 등급은 필수입니다.
            - 기업 규모는 AI 예측 조건이며 기존 KEEP 표본의 필터 조건에 추가하지 않습니다.
            ### 6. Response Header
            - Content-Type: application/json
            ### 7. Response Body
            - success: true, code: COMMON_200, message: 연봉 시뮬레이션 성공.
            - 캐싱 실패 시에도 정상 결과를 반환하고 message에 다음 안내를 추가합니다.
              "응답 캐싱에 실패했습니다. 현재 스펙으로 저장 요청 시 별도의 AI 호출 결과값이 저장되게 됩니다"
            - data: baseSpecProfileId, companyGroupScale, base, simulated, preparationPeriod, estimatedCost, decisionCriteria.
            - base·simulated는 salaryResults만 포함하며 추가 조건이 없으면 simulated는 필드 생략 없이 null입니다.
            - preparationPeriod: 추가 목표 조건을 달성하는 데 필요한 준비 기간만 한 줄로 반환합니다.
            - estimatedCost: 추가 목표 조건의 원화 비용·범위 또는 비용 상태만 간단히 반환합니다.
            - decisionCriteria: 희망 직무에서 목표 조건의 활용도만 해요체 한 문장으로 반환합니다.
            - 준비 정보에는 AI의 판단 과정·산정 가정·포함/제외 항목을 표시하지 않습니다.
            - 세 준비 정보는 현재 보유 스펙을 제외한 추가 목표 기준이며, 추가 조건이 없으면 모두 null입니다.
            - salaryResults: annual 0, 3, 5 순서와 원 단위 annualIncomeMin, annualIncomeExpected, annualIncomeMax.
            - topPercent는 기존 비교 표본 내 첫 취업 예상 연봉의 상위 비율이며 3·5년 차는 null입니다.
            - 어느 한쪽이라도 실패하면 전체 요청을 오류로 반환합니다. 타인·미존재 기준 스펙은 동일하게 404입니다.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "시뮬레이션 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값·분류 조건 오류 또는 비교 표본 부족"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "본인 현재 스펙 또는 참조 정보 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "AI 요청량 제한"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "분석 입력 준비 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "AI 요청·응답 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "AI 사용 한도 초과 또는 요청 중단"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "504", description = "AI 응답 시간 초과")
    })
    @PostMapping(value = "/api/spec-profile/target", consumes = "application/json", produces = "application/json")
    ApiResponse<SpecProfileSimulationResDto> simulateAnnualSalary(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody SpecProfileSimulationReqDto request);

    @Operation(summary = "회원 스펙 연봉 분석 요청 중단", description = """
            ### 1. API 역할
            - JWT 회원 본인의 QUEUED 또는 IN_PROGRESS 실행을 CANCELED로 변경합니다.
            - 이미 CANCELED이면 최초 중단 시각을 유지하고 성공 응답을 반환합니다.
            - COMPLETED 또는 FAILED이면 RUN_NOT_STOPPABLE(409)을 반환합니다.
            - 중단 상태와 단계·실행 취소 로그를 함께 저장한 뒤 SSE canceled를 전송하고 구독을 종료합니다.
            - 완료 단계는 COMPLETED, 진행 단계는 CANCELED, 미시작 단계는 QUEUED를 유지합니다.
            - 진행 중인 작업은 단계 경계에서 중단하며, 이미 전송된 AI 요청의 즉시 종료는 보장하지 않습니다.
            - 완료·실패와 중단이 경합하면 먼저 확정된 종료 상태를 유지하고, 취소 후 결과를 저장하지 않습니다.
            - 실행 이력과 요청 스냅샷은 삭제하지 않습니다.
            ### 2. Request Header
            - Authorization: Bearer {accessToken}. 요청 Content-Type 지정은 필요하지 않습니다.
            ### 3. Path Variable
            - runId: 중단할 분석 실행 PK. 필수 양의 정수(int64).
            ### 4. Query String
            - 없음
            ### 5. Request Body
            - 없음. 요청 본문을 전송하지 않습니다.
            ### 6. Response Header
            - Content-Type: application/json
            ### 7. Response Body
            - success: true, code: COMMON_200, message: 요청이 성공적으로 처리되었습니다.
            - data: runId, specProfileId, status(CANCELED), canceledAt.
            - canceledAt은 최초 중단 시각이며 한국 시간 +09:00의 ISO 8601 문자열입니다. 재요청 시 변경하지 않습니다.
            - INVALID_REQUEST(400): 1 미만의 실행 ID. COMMON_400(400): 경로 변수 형식 오류.
            - AUTH_401(401): 인증 실패. AUTH_403(403): 접근 권한 없음.
            - 타인 소유 실행과 미존재 실행은 RUN_NOT_FOUND(404)로 동일하게 처리합니다.
            - RUN_NOT_STOPPABLE(409): 이미 완료 또는 실패한 실행. COMMON_500(500): 중단 저장 실패.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "중단 성공 또는 이미 중단된 실행",
                content = @Content(schema = @Schema(implementation = SpecProfileAnalyzationRunStopResDto.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "실행 ID 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "본인 실행 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 완료 또는 실패한 실행"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "중단 저장 실패")
    })
    @PatchMapping(value = "/api/spec-profile/analyzation/runs/{runId}/stop", produces = "application/json")
    ApiResponse<SpecProfileAnalyzationRunStopResDto> stopAnalyzationRun(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "중단할 분석 실행 PK", required = true) @PathVariable("runId") Long runId);

    @Operation(summary = "회원 스펙 연봉 분석 진행 상태 SSE 구독", description = """
            ### 1. API 역할
            - JWT 회원 본인의 특정 실행 상태와 9개 단계의 진행 상황을 구독합니다.
            - 최초 연결과 재연결 모두 최신 snapshot으로 동기화하고 이후 progress를 전달합니다.
            - completed, failed, canceled 전송 후 연결을 종료합니다. 연결 해제는 분석 취소가 아닙니다.
            ### 2. Request Header
            - Authorization: Bearer {accessToken}, Accept: text/event-stream.
            - Last-Event-ID: 마지막 이벤트 로그 PK인 양의 정수. 선택이며 동일 실행에 속해야 합니다.
            ### 3. Path Variable
            - runId: 구독할 분석 실행 PK. 필수 양의 정수(int64).
            ### 4. Query String
            - 없음
            ### 5. Request Body
            - 없음
            ### 6. Response Header
            - Content-Type: text/event-stream, Cache-Control: no-cache, X-Accel-Buffering: no.
            ### 7. Response Body
            - SSE 이벤트: snapshot, progress, completed, failed, canceled. heartbeat는 15초마다 주석으로 전송합니다.
            - id는 eventLogId와 같습니다. data는 공통 JSON 응답으로 감싸지 않은 진행 상태입니다.
            - runId, eventLogId, status, totalSteps, completedStepCount, currentStepOrder, steps, resultUrl, occurredAt.
            - 실행과 단계 status는 QUEUED, IN_PROGRESS, CANCELED, COMPLETED, FAILED입니다.
            - steps: order는 Step.order, code는 ENUM 이름, name은 Step.displayName, status는 단계 상태입니다.
            - 완료된 단계 수를 반환하며 실행 종료 시 currentStepOrder는 null입니다.
            - resultUrl은 COMPLETED에서만 존재합니다. occurredAt은 한국 시간 +09:00 ISO 8601입니다.
            - 과거 단계 이력이 없으면 단계 status와 completedStepCount, occurredAt은 null입니다.
            - 과거 이벤트가 없는 실행은 eventLogId가 null이며 SSE id를 생략합니다.
            - 잘못된 ID 또는 커서는 INVALID_REQUEST(400), 타인 실행과 미존재 실행은 RUN_NOT_FOUND(404)입니다.
            - 연결 제한 시간의 기본값은 30분이며 종료 이벤트 없이 끊기면 클라이언트가 재접속해야 합니다.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "SSE 구독 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "실행 ID 또는 커서 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "본인 실행 없음")
    })
    @GetMapping(value = "/api/spec-profile/analyzation/runs/{runId}/events", produces = "text/event-stream")
    ResponseEntity<SseEmitter> subscribeAnalyzationEvents(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "구독할 분석 실행 PK", required = true) @PathVariable("runId") Long runId,
            @Parameter(description = "동일 실행의 마지막 이벤트 로그 PK") @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId);

    @Operation(summary = "회원 스펙 연봉 분석 실행 상태 및 결과 조회", description = """
            ### 1. API 역할
            - JWT 회원 본인의 특정 분석 실행 상태와 저장된 분석 결과를 조회합니다.
            - runId에 해당하는 실행을 조회하며 최신 실행으로 대체하지 않습니다.
            - COMPLETED에서만 결과를 반환하고 QUEUED, IN_PROGRESS, CANCELED, FAILED에서는 result가 null입니다.
            - 요청 당시 스펙 스냅샷과 저장된 통계를 사용하며 CSV 필터링이나 AI 요청을 다시 수행하지 않습니다.
            - 단계별 진행 정보와 SSE 이벤트 로그는 포함하지 않습니다.
            ### 2. Request Header
            - Authorization: Bearer {accessToken}. 요청 Content-Type 지정은 필요하지 않습니다.
            ### 3. Path Variable
            - runId: 분석 요청 API에서 반환받은 분석 실행 PK. 필수 정수(int64).
            ### 4. Query String
            - 없음
            ### 5. Request Body
            - 없음
            ### 6. Response Header
            - Content-Type: application/json
            ### 7. Response Body
            - success: true, code: COMMON_200, message: 연봉 분석 실행 상태 및 결과 조회 성공.
            - data: runId, specProfileId, status, result. 실패·취소 실행도 조회 성공 시 success는 true입니다.
            - result: summary, salaryResults, comparisonGroup, appliedSpecs, statistics, recommendation, salaryNotice.
            - salaryResults: annual 0, 3, 5 순서의 원 단위 AI 예측 하한·예상·상한. careerLabel은 제외합니다.
            - topPercent는 첫 취업(annual=0)의 저장된 비교 표본 내 상위 비율이며 3·5년 차는 null입니다.
            - comparisonGroup: 직무 분류 코드·이름, 표본 수, 관측 연도, 비교 집단 설명.
            - appliedSpecs: birthYear, residenceRegion, jobId, jobName 및 요청 당시 학력·자격증·어학·경험 목록.
            - statistics: sampleCount, annualSalaryMinKrw, annualSalaryMaxKrw, annualSalaryMeanKrw,
              annualSalaryModesKrw, modeFrequency, annualSalaryP25Krw, annualSalaryMedianKrw, annualSalaryP75Krw.
            - 최빈값이 없으면 annualSalaryModesKrw는 빈 배열입니다. 미완료 result는 필드 생략 없이 null입니다.
            - 타인 소유 실행과 미존재 실행은 RUN_NOT_FOUND(404)로 동일하게 처리합니다.
            - 완료 실행에 연차 결과나 필수 통계가 누락되면 INCOMPLETE_SAVED_RESULT(500),
              저장된 스냅샷을 읽을 수 없으면 INVALID_SNAPSHOT(500)을 반환합니다.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "실행 상태 및 결과 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "실행 ID 형식 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "본인 소유 실행 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "저장 결과 또는 스냅샷 불완전")
    })
    @GetMapping(value = "/api/spec-profile/analyzation/runs/{runId}", produces = "application/json")
    ApiResponse<SpecProfileAnalyzationRunResultResDto> getAnalyzationRunResult(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "조회할 분석 실행 PK", required = true) @PathVariable("runId") Long runId);

    @Operation(summary = "회원 스펙 조회", description = """
            ### 1. API 역할
            - JWT 회원 본인의 CURRENT 프로필과 임시 저장된 최신 내용을 조회합니다.
            - 기본 정보, 희망 직무, 학력, 자격증, 어학 성적, 활동 경험을 반환합니다.
            - 연봉 진단은 실행하지 않습니다.
            ### 2. Request Header
            - Authorization: Bearer {accessToken}. 요청 Content-Type 지정은 필요하지 않습니다.
            ### 3. Path Variable
            - 없음
            ### 4. Query String
            - 없음
            ### 5. Request Body
            - 없음
            ### 6. Response Header
            - Content-Type: application/json
            ### 7. Response Body
            - success: true, code: COMMON_200, message: 회원 스펙 조회 성공.
            - data: specProfileId, type(CURRENT), birthYear, residenceRegion.
            - desiredJobId는 DesiredJob PK이며 저장 요청의 jobId(Job PK)와 다릅니다.
            - desiredJobName은 연결된 직무의 이름입니다.
            - academicAbilities는 학력과 전공 ID·이름·계열을 포함합니다.
            - certificates는 보유 자격증 PK, 자격증 ID·이름과 취득 연월을 포함합니다.
            - languageExamScores는 성적 PK, 시험 ID·이름, 숫자 점수·등급, 취득·만료 연월을 포함합니다.
            - experiences는 경험 PK, 유형, 활동명, 시작·종료 연월, 설명 및 수상 정보를 포함합니다.
            - 미입력 단일 값은 null, 목록은 []입니다. 각 목록은 항목 PK 오름차순입니다.
            - 연월은 YYYY-MM, createdAt·updatedAt은 한국 시간 +09:00의 ISO 8601 문자열입니다.
            - 오류 코드는 AUTH_401, SPEC_PROFILE_404_1, SPEC_PROFILE_500_1, SPEC_PROFILE_500_3입니다.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원 스펙 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "CURRENT 프로필 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "프로필 또는 전공 연결 무결성 오류")
    })
    @GetMapping(value = "/api/spec-profile", produces = "application/json")
    ApiResponse<SpecProfileResDto> getCurrentProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId);

    @Operation(summary = "회원 스펙 임시 저장", description = """
            ### 1. API 역할
            - 본인 CURRENT 프로필과 회원의 희망 직무를 저장합니다. 연봉 진단은 실행하지 않습니다.
            - 해당 프로필의 최신 분석 실행이 QUEUED 또는 IN_PROGRESS이면 ANALYSIS_ALREADY_RUNNING(409)으로 거절합니다.
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
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "최신 분석 실행이 대기 또는 진행 중"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "어학 데이터 미초기화 또는 프로필 무결성 오류")
    })
    @PutMapping(value = "/api/spec-profile/temporary", consumes = "application/json", produces = "application/json")
    ResponseEntity<ApiResponse<Void>> saveTemporary(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody SpecProfileTemporarySaveReqDto request);

    @Operation(summary = "회원 스펙 연봉 분석 실행 요청", description = """
            ### 1. API 역할
            - 회원 본인의 CURRENT 스펙 스냅샷, 실행 이력 및 최초 접수 이벤트를 저장합니다.
            - 출생연도, 거주지역, 희망직무 및 최소 1개의 유효한 학력이 필요합니다.
            - 해당 프로필의 최신 실행이 QUEUED 또는 IN_PROGRESS이면 접수와 임시 저장을 거절합니다.
            - 커밋 후 분석 요청 이벤트를 전용 실행 풀에서 비동기로 처리합니다.
            - 비동기 실행은 IN_PROGRESS로 전환 후 누적 스펙 조건에 맞는 KEEP 표본을 패널당 한 연도씩 선택합니다. 실제 연봉 예측은 구현 예정입니다.
            ### 2. Request Header
            - Authorization: Bearer {accessToken}. 요청 Content-Type 지정은 필요하지 않습니다.
            ### 3. Path Variable
            - 없음
            ### 4. Query String
            - 없음
            ### 5. Request Body
            - 없음
            ### 6. Response Header
            - Content-Type: application/json
            ### 7. Response Body
            - success: true, code: COMMON_200, message: 연봉 분석 요청이 접수되었습니다.
            - data: runId, specProfileId, status(QUEUED), totalSteps(9), completedStepCount(0).
            - steps: 실제 분석 처리 순서에 따른 9개 단계의 코드, 순서, 표시명.
              RUN_START → SNAPSHOT_LOAD → CLASSIFICATION_DATA_LOAD → SCORE_BAND_CLASSIFICATION → SAMPLE_SELECTION
              → STATISTICS_AND_PROMPT_BUILD → SALARY_PREDICTION → RESULT_COMPOSITION → RESULT_SAVE.
            - eventsUrl, resultUrl: 실행별 상대 경로. eventsUrl로 SSE 진행 상태를 구독하고 resultUrl로 상태와 결과를 조회합니다.
            - requestedAt: 최초 요청 시각을 한국 시간 +09:00의 ISO 8601 문자열로 반환합니다.
            - 오류 코드: INVALID_REQUEST(400), AUTH_401(401), AUTH_403(403), NOT_FOUND(404),
              ANALYSIS_ALREADY_RUNNING(409), INTERNAL_SERVER_ERROR 또는 COMMON_500(500).
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "분석 요청 접수 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "분석 입력 누락 또는 유효하지 않은 스펙"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "회원 또는 CURRENT 프로필 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "최신 분석이 대기 또는 진행 중"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "분석 실행 저장 실패")
    })
    @PostMapping(value = "/api/spec-profile/analyzation/run", produces = "application/json")
    ApiResponse<SpecProfileAnalyzationRunResDto> requestRun(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId);
}
