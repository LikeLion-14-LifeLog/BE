package com.lifelog.domain.certification.service;
import com.lifelog.domain.certification.dto.CertificationInitResDto;
import com.lifelog.domain.certification.exception.CertificationErrorCode;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificationService {
    private final CertificationCsvReader reader;
    private final CertificationInitService initService;
    /** 트랜잭션 밖에서 파일을 검증한 뒤 저장한다. @return 초기화 집계 */
    public CertificationInitResDto initFromCsv() {
        log.info("[CertificationService] 자격증 초기화 저장 시작 | initFromCsv() - START");
        var rows = reader.read();
        try {
            var result = initService.initialize(rows);
            log.info("[CertificationService] 자격증 초기화 저장 종료 | initFromCsv() - END | result: {}", result);
            return result;
        } catch (DataAccessException | TransactionException e) {
            log.error("자격증 초기화 저장 실패", e);
            throw new CustomException(CertificationErrorCode.INIT_FAILED);
        }
    }
}
