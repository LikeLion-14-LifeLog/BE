package com.lifelog.domain.specprofile.controller;

import com.lifelog.domain.specprofile.dto.SpecProfileTemporarySaveReqDto;
import com.lifelog.domain.specprofile.dto.SpecProfileResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunResultResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunStopResDto;
import com.lifelog.domain.specprofile.service.SpecProfileService;
import com.lifelog.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class SpecProfileController implements SpecProfileControllerDocs {
    private final SpecProfileService service;

    @Override
    public ApiResponse<SpecProfileAnalyzationRunStopResDto> stopAnalyzationRun(Long memberId, Long runId) {
        return ApiResponse.success("요청이 성공적으로 처리되었습니다.", service.stopAnalyzationRun(memberId, runId));
    }

    @Override
    public ResponseEntity<SseEmitter> subscribeAnalyzationEvents(Long memberId, Long runId, String lastEventId) {
        SseEmitter emitter = service.subscribeAnalyzationEvents(memberId, runId, lastEventId);
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no").body(emitter);
    }

    @Override
    public ApiResponse<SpecProfileAnalyzationRunResultResDto> getAnalyzationRunResult(Long memberId, Long runId) {
        return ApiResponse.success("연봉 분석 실행 상태 및 결과 조회 성공", service.getAnalyzationRunResult(memberId, runId));
    }

    @Override
    public ApiResponse<SpecProfileResDto> getCurrentProfile(Long memberId) {
        return ApiResponse.success("회원 스펙 조회 성공", service.getCurrentProfile(memberId));
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> saveTemporary(Long memberId, SpecProfileTemporarySaveReqDto request) {
        service.saveTemporary(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("COMMON_201", "회원 스펙 임시 저장 성공", null));
    }

    @Override
    public ApiResponse<SpecProfileAnalyzationRunResDto> requestRun(Long memberId) {
        return ApiResponse.success("연봉 분석 요청이 접수되었습니다.", service.requestSpecProfileAnalyzationRun(memberId));
    }
}
