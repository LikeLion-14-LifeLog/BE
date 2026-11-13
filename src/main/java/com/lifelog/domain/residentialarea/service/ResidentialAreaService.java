package com.lifelog.domain.residentialarea.service;

import com.lifelog.domain.residentialarea.dto.ResidentialAreaListResDto;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.residentialarea.mapper.ResidentialAreaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResidentialAreaService {

    private final ResidentialAreaMapper residentialAreaMapper;

    /**
     * 국내 거주 지역을 ENUM 선언 순서대로 조회한다.
     * @return 지역 코드와 화면 표시명을 포함한 국내 17개 지역 목록
     */
    public ResidentialAreaListResDto getResidentialAreas() {
        log.info("[ResidentialAreaService] 거주 지역 조회 | getResidentialAreas() - START");
        /*
         * (1) 국내 지역 응답 생성
         * - 학력 소재지에 사용하는 외국을 제외하고 기존 거주 지역 목록을 유지한다.
         */
        ResidentialAreaListResDto result = ResidentialAreaListResDto.create(
                Arrays.stream(ResidentialArea.values())
                        .filter((ResidentialArea area) -> area != ResidentialArea.FOREIGN)
                        .map(residentialAreaMapper::toResidentialAreaResDto)
                        .toList());
        log.info("[ResidentialAreaService] 거주 지역 조회 | getResidentialAreas() - END | count: {}",
                result.residentialAreas().size());
        return result;
    }
}
