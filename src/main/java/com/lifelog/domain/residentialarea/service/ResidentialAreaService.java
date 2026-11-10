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
     * ENUM에 정의된 전체 거주 지역을 선언 순서대로 조회한다.
     * @return 지역 코드와 화면 표시명을 포함한 전체 지역 목록
     */
    public ResidentialAreaListResDto getResidentialAreas() {
        log.info("[ResidentialAreaService] 거주 지역 조회 | getResidentialAreas() - START");
        /*
         * (1) 전체 지역 응답 생성
         * - ENUM 선언 순서를 유지하며 코드와 표시명으로 변환한다.
         */
        ResidentialAreaListResDto result = ResidentialAreaListResDto.create(
                Arrays.stream(ResidentialArea.values())
                        .map(residentialAreaMapper::toResidentialAreaResDto)
                        .toList());
        log.info("[ResidentialAreaService] 거주 지역 조회 | getResidentialAreas() - END | count: {}",
                result.residentialAreas().size());
        return result;
    }
}
