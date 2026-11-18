package com.lifelog.domain.dashboard.controller;

import com.lifelog.domain.dashboard.dto.DashboardResDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "Dashboard", description = "회원 대시보드 조회 API")
@SecurityRequirement(name = "bearerAuth")
public interface DashboardControllerDocs {
    @Operation(summary = "대시보드 정보 조회", description = """
            ### 1. API 역할
            - 본인 CURRENT에 연결된 미삭제 분석 실행을 createdAt DESC, ID DESC로 한 건 선택해요.
            - 최신 실행이 미완료이면 해당 상태와 result:null을 반환하며 과거 완료 결과로 대체하지 않아요.
            - 완료 결과의 annual=0 예상 초봉·상위 비율과 분석 당시 스냅샷의 핵심 스펙 최대 4개를 반환해요.
            - 공고는 조회 시점의 기존 맞춤 조건으로 마감 임박순 최대 3개와 전체 건수를 반환해요.
            - 추가 직무·지역 필터는 없으며 신입·경력무관 조건도 기존 설정을 따라요.
            - AI·수집·재분석·저장·캐시 변경 없이 같은 DB 스냅샷에서 읽어요.
            ### 2. Request Header
            - Authorization: Bearer {accessToken}. Swagger Authorize에서 로그인 토큰을 설정해요.
            ### 3. Path Variable
            - 없음
            ### 4. Query String
            - 없음. 공고 개수 3개와 마감 정렬은 고정이에요.
            ### 5. Request Body
            - 없음
            ### 6. Response Header
            - Content-Type: application/json
            ### 7. Response Body
            - 성공 응답은 대시보드 전용 status:200, message, data 형식이에요.
            - data.analysis: runId, specProfileId, status, requestedAt(서울 시각), result. 이력이 없으면 null이에요.
            - result: annual(0), expectedStartingSalary(원), topPercent, salaryNotice, coreSpecs예요.
            - 상위 비율은 비교 집단 내 순위이며 서류 통과율·합격 확률이 아니에요.
            - coreSpecs는 대표 학력·동일 학점·첫 자격증·분석 당시 희망 직무 중 값이 있는 칩만 반환해요.
            - recommendedJobPostings: personalized, totalCount, jobPostings. 희망 직무가 없으면 기존 전체 조회 정책을 따라요.
            - 공고별 예상 연봉은 기존 로직에서 현재 null이며 SKILL 요건이 없으면 skillTags:[]예요.
            - deadlineType은 FIXED·ALWAYS·UNTIL_FILLED, dDay는 서울 기준 남은 일수(당일 0, 미지정 null)예요.
            - null과 빈 배열을 생략하지 않아요. 오류는 기존 공통 응답(success/code/message) 형식을 유지해요.
            """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "대시보드 조회 성공", content = @Content(
                schema = @Schema(implementation = DashboardResDto.class), examples = {
                    @ExampleObject(name = "분석 완료", value = """
                            {"status":200,"message":"요청이 성공적으로 처리되었습니다.","data":{
                              "analysis":{"runId":84,"specProfileId":10,"status":"COMPLETED","requestedAt":"2026-11-18T09:30:00",
                                "result":{"annual":0,"expectedStartingSalary":36000000,"topPercent":35.0,"salaryNotice":"저장된 비교 표본 기반 안내",
                                  "coreSpecs":[{"type":"EDUCATION","label":"대학교 · 컴퓨터공학과"},{"type":"GPA","label":"학점 3.8 / 4.5"},
                                    {"type":"CERTIFICATE","label":"정보처리기사 취득"},{"type":"DESIRED_JOB","label":"희망 직무 백엔드 개발자"}]}},
                              "recommendedJobPostings":{"personalized":true,"totalCount":24,"jobPostings":[{
                                "jobPostingId":101,"title":"백엔드 개발자","companyName":"예시 기업","workRegion":"서울 강남구",
                                "expectedSalaryMin":null,"expectedSalaryMax":null,"deadlineType":"FIXED","dDay":7,"skillTags":["Java","Spring Boot"]}]}}}
                            """),
                    @ExampleObject(name = "분석·공고 없음", value = """
                            {"status":200,"message":"요청이 성공적으로 처리되었습니다.","data":{"analysis":null,
                              "recommendedJobPostings":{"personalized":false,"totalCount":0,"jobPostings":[]}}}
                            """),
                    @ExampleObject(name = "분석 진행 중", value = """
                            {"status":200,"message":"요청이 성공적으로 처리되었습니다.","data":{
                              "analysis":{"runId":85,"specProfileId":10,"status":"IN_PROGRESS","requestedAt":"2026-11-18T10:00:00","result":null},
                              "recommendedJobPostings":{"personalized":true,"totalCount":0,"jobPostings":[]}}}
                            """)
                })),
        @ApiResponse(responseCode = "401", description = "AUTH_401: 인증 실패", content = @Content(examples = @ExampleObject(value = """
                {"success":false,"code":"AUTH_401","message":"로그인이 필요하거나 인증 정보가 유효하지 않습니다."}
                """))),
        @ApiResponse(responseCode = "500", description = "INCOMPLETE_SAVED_RESULT / INVALID_SNAPSHOT / COMMON_500", content = @Content(
                examples = {
                    @ExampleObject(name = "불완전 저장 결과", value = """
                            {"success":false,"code":"INCOMPLETE_SAVED_RESULT","message":"저장된 연봉 분석 결과가 불완전합니다. 재분석이 필요합니다.","data":null}
                            """),
                    @ExampleObject(name = "손상된 스냅샷", value = """
                            {"success":false,"code":"INVALID_SNAPSHOT","message":"저장된 분석 스냅샷을 읽을 수 없습니다.","data":null}
                            """)
                }))
    })
    @GetMapping(value = "/api/dashboard", produces = "application/json")
    DashboardResDto getDashboard(@Parameter(hidden = true) @AuthenticationPrincipal Long memberId);
}
