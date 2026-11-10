package com.lifelog.domain.certification.controller;
import com.lifelog.domain.certification.dto.CertificationInitResDto;
import com.lifelog.domain.certification.dto.CertificationListResDto;
import com.lifelog.domain.certification.enums.CertificationType;
import com.lifelog.domain.certification.enums.CertificationMainCategory;
import com.lifelog.global.response.SliceResponse;
import com.lifelog.domain.certification.service.CertificationService;
import com.lifelog.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequiredArgsConstructor
public class CertificationController implements CertificationControllerDocs {
    private final CertificationService service;
    @Override
    public ApiResponse<SliceResponse<CertificationListResDto>> searchCertifications(
            String keyword, CertificationType type, CertificationMainCategory mainCategory, int page, int size) {
        return ApiResponse.success("자격증 데이터 조회 성공",
                service.searchCertifications(keyword, type, mainCategory, page, size));
    }

    @Override
    public ApiResponse<CertificationInitResDto> initCertifications() {
        return ApiResponse.success("[관리자] 자격증 데이터 초기화 성공", service.initFromCsv());
    }
}
