package com.lifelog.domain.residentialarea.controller;

import com.lifelog.domain.residentialarea.dto.ResidentialAreaListResDto;
import com.lifelog.domain.residentialarea.service.ResidentialAreaService;
import com.lifelog.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ResidentialAreaController implements ResidentialAreaControllerDocs {

    private final ResidentialAreaService residentialAreaService;

    @Override
    public ApiResponse<ResidentialAreaListResDto> getResidentialAreas() {
        return ApiResponse.success("요청이 성공적으로 처리되었습니다.",
                residentialAreaService.getResidentialAreas());
    }
}
