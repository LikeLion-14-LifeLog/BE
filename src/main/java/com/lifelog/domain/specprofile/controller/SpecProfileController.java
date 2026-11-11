package com.lifelog.domain.specprofile.controller;

import com.lifelog.domain.specprofile.dto.SpecProfileTemporarySaveReqDto;
import com.lifelog.domain.specprofile.service.SpecProfileService;
import com.lifelog.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SpecProfileController implements SpecProfileControllerDocs {
    private final SpecProfileService service;

    @Override
    public ResponseEntity<ApiResponse<Void>> saveTemporary(Long memberId, SpecProfileTemporarySaveReqDto request) {
        service.saveTemporary(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("COMMON_201", "회원 스펙 임시 저장 성공", null));
    }
}

