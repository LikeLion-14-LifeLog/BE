package com.lifelog.domain.major.service;

import com.lifelog.domain.major.dto.MajorCsvReadResult;
import com.lifelog.domain.major.dto.MajorInitResDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MajorService {

    private final MajorCsvReader majorCsvReader;
    private final MajorInitService majorInitService;

    /**
     * 트랜잭션 없이 원본을 검증한 뒤 별도 빈의 저장 트랜잭션을 호출한다.
     * @return 원본·중복·등록·변경·동일 건수
     */
    public MajorInitResDto initFromCsv() {
        log.info("[MajorService] 전공 초기화 | initFromCsv() - START");
        MajorCsvReadResult source = majorCsvReader.read();
        MajorInitResDto result = majorInitService.initialize(source);
        log.info("[MajorService] 전공 초기화 | initFromCsv() - END | result: {}", result);
        return result;
    }
}
