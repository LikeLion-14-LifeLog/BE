package com.lifelog.domain.major.controller;

import com.lifelog.domain.major.dto.MajorInitResDto;
import com.lifelog.domain.major.dto.MajorListResDto;
import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.global.response.SliceResponse;
import com.lifelog.global.response.ApiResponse;
import com.lifelog.domain.major.service.MajorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MajorController implements MajorControllerDocs {

    private final MajorService majorService;

    @Override
    public ApiResponse<SliceResponse<MajorListResDto>> searchMajors(
            String keyword, MajorMainField mainField, int page, int size) {
        return ApiResponse.success("요청이 성공적으로 처리되었습니다.",
                majorService.searchMajors(keyword, mainField, page, size));
    }

    @Override
    public ApiResponse<MajorInitResDto> initMajors() {
        return ApiResponse.success("전공 데이터 초기화 성공", majorService.initFromCsv());
    }
}
