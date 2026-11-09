package com.lifelog.domain.major.controller;

import com.lifelog.domain.major.dto.MajorInitResDto;
import com.lifelog.global.response.ApiResponse;
import com.lifelog.domain.major.service.MajorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MajorController implements MajorControllerDocs {

    private final MajorService majorService;

    @Override
    public ApiResponse<MajorInitResDto> initMajors() {
        return ApiResponse.success("전공 데이터 초기화 성공", majorService.initFromCsv());
    }
}
