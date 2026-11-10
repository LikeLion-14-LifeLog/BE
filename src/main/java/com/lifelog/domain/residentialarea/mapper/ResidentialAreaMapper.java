package com.lifelog.domain.residentialarea.mapper;

import com.lifelog.domain.residentialarea.dto.ResidentialAreaResDto;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import org.springframework.stereotype.Component;

@Component
public class ResidentialAreaMapper {

    /**
     * 거주 지역 ENUM을 코드와 화면 표시명을 가진 응답으로 변환한다.
     * @param area 거주 지역 ENUM
     * @return 지역 코드와 한글 표시명
     */
    public ResidentialAreaResDto toResidentialAreaResDto(ResidentialArea area) {
        return ResidentialAreaResDto.create(area.name(), area.getDisplayName());
    }
}
