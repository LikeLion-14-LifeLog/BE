package com.lifelog.domain.certification.controller;
import com.lifelog.domain.certification.dto.CertificationInitResDto;
import com.lifelog.domain.certification.service.CertificationService;
import com.lifelog.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequiredArgsConstructor
public class CertificationController implements CertificationControllerDocs {
    private final CertificationService service;
    @Override
    public ApiResponse<CertificationInitResDto> initCertifications() {
        return ApiResponse.success("[관리자] 자격증 데이터 초기화 성공", service.initFromCsv());
    }
}
