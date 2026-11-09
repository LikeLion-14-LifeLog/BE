package com.lifelog.domain.major.service;

import com.lifelog.domain.major.dto.MajorCsvReadResult;
import com.lifelog.domain.major.dto.MajorInitResDto;
import com.lifelog.domain.major.dto.MajorListResDto;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.domain.major.exception.MajorErrorCode;
import com.lifelog.domain.major.mapper.MajorMapper;
import com.lifelog.domain.major.repository.MajorRepository;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.exception.CommonErrorCode;
import com.lifelog.global.response.SliceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MajorService {

    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final int MAX_SIZE = 100;

    private final MajorCsvReader majorCsvReader;
    private final MajorInitService majorInitService;
    private final MajorRepository majorRepository;
    private final MajorMapper majorMapper;

    /**
     * 검색어·대계열 조건으로 전공을 코드 오름차순 조회한다.
     * @param keyword 학과명 검색어, 공백이면 전체 조회
     * @param mainField 대계열, null이면 전체 계열
     * @param page 1부터 시작하는 페이지 번호
     * @param size 페이지 크기 (1~100)
     * @return 전공 목록과 전체 건수를 포함한 페이지 응답
     */
    @Transactional(readOnly = true)
    public SliceResponse<MajorListResDto> searchMajors(String keyword, MajorMainField mainField, int page, int size) {
        log.info("[MajorService] 전공 조회 | searchMajors() - START | mainField: {}, page: {}, size: {}",
                mainField, page, size);
        /* (1) 요청 검증: 페이지 범위와 정규화된 검색어 길이를 검사한다. */
        if (page < 1) {
            throw new CustomException(CommonErrorCode.INVALID_PAGE);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new CustomException(CommonErrorCode.INVALID_SIZE);
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        /* (2) 페이지 조회: 고유 학과코드 순으로 조회하고 같은 조건의 전체 건수를 구한다. */
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by("code"));
        Page<Major> result = majorRepository.searchMajors(normalizedKeyword, mainField, pageable);
        /* (3) 응답 변환: Mapper로 목록을 변환하고 공통 페이징 정보를 결합한다. */
        SliceResponse<MajorListResDto> response = SliceResponse.of(result, majorMapper.toMajorListResDto(result.getContent()));
        log.info("[MajorService] 전공 조회 | searchMajors() - END | resultSize: {}, totalElements: {}",
                result.getNumberOfElements(), result.getTotalElements());
        return response;
    }

    /**
     * 공백 검색어를 null로 바꾸고 앞뒤 공백 제거 후 길이를 검증한다.
     * @param keyword 원본 검색어
     * @return 정규화된 검색어, 검색 조건이 없으면 null
     */
    private String normalizeKeyword(String keyword) {
        log.debug("[MajorService] 검색어 정규화 | normalizeKeyword() - START");
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.strip();
        if (normalized != null && normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new CustomException(MajorErrorCode.INVALID_KEYWORD);
        }
        log.debug("[MajorService] 검색어 정규화 | normalizeKeyword() - END");
        return normalized;
    }

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
