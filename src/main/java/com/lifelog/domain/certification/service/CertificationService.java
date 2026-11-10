package com.lifelog.domain.certification.service;
import com.lifelog.domain.certification.dto.CertificationInitResDto;
import com.lifelog.domain.certification.dto.CertificationListResDto;
import com.lifelog.domain.certification.enums.CertificationType;
import com.lifelog.domain.certification.enums.CertificationMainCategory;
import com.lifelog.domain.certification.repository.CertificationRepository;
import com.lifelog.domain.certification.mapper.CertificationMapper;
import com.lifelog.global.exception.CommonErrorCode;
import com.lifelog.global.response.SliceResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
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
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final int MAX_SIZE = 100;
    private final CertificationCsvReader reader;
    private final CertificationInitService initService;
    private final CertificationRepository repository;
    private final CertificationMapper mapper;

    /**
     * 이름, 유형, 대분류로 검색하고 이름과 ID 오름차순으로 페이지를 반환한다.
     * @param keyword 이름 검색어, 공백이면 전체 조회
     * @param type 유형, null이면 전체 유형
     * @param mainCategory 대분류, null이면 전체 대분류
     * @param page 1부터 시작하는 페이지 번호
     * @param size 페이지 크기 (1~100)
     * @return 자격증 목록과 페이지 집계
     */
    @Transactional(readOnly = true)
    public SliceResponse<CertificationListResDto> searchCertifications(
            String keyword, CertificationType type, CertificationMainCategory mainCategory, int page, int size) {
        log.info("[CertificationService] 자격증 조회 | searchCertifications() - START | type: {}, mainCategory: {}, page: {}, size: {}",
                type, mainCategory, page, size);
        /* (1) 페이지 범위와 정규화된 검색어 길이를 검증한다. */
        if (page < 1) {
            throw new CustomException(CommonErrorCode.INVALID_PAGE);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new CustomException(CommonErrorCode.INVALID_SIZE);
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        /* (2) 선택 조건을 AND로 적용하고 이름과 ID로 정렬한다. */
        var pageable = PageRequest.of(page - 1, size, Sort.by("name").ascending().and(Sort.by("id").ascending()));
        var result = repository.searchCertifications(normalizedKeyword, type, mainCategory, pageable);
        /* (3) 목록을 변환하고 실제 전체 집계를 포함한 페이지 응답을 만든다. */
        var response = SliceResponse.of(result, mapper.toCertificationListResDto(result.getContent()));
        log.info("[CertificationService] 자격증 조회 | searchCertifications() - END | resultSize: {}, totalElements: {}",
                result.getNumberOfElements(), result.getTotalElements());
        return response;
    }

    /**
     * 앞뒤 공백을 제거하고 공백 검색어를 null로 정규화한다.
     * @param keyword 원본 검색어
     * @return 정규화한 검색어, 검색 조건이 없으면 null
     */
    private String normalizeKeyword(String keyword) {
        log.debug("[CertificationService] 검색어 정규화 | normalizeKeyword() - START");
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.strip();
        if (normalized != null && normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new CustomException(CertificationErrorCode.INVALID_KEYWORD);
        }
        log.debug("[CertificationService] 검색어 정규화 | normalizeKeyword() - END");
        return normalized;
    }
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
