package com.lifelog.domain.major.service;

import com.lifelog.domain.major.dto.MajorCsvReadResult;
import com.lifelog.domain.major.dto.MajorCsvRow;
import com.lifelog.domain.major.dto.MajorInitResDto;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.major.exception.MajorErrorCode;
import com.lifelog.global.exception.CustomException;
import com.lifelog.domain.major.mapper.MajorMapper;
import com.lifelog.domain.major.repository.MajorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MajorInitService {

    private final MajorRepository majorRepository;
    private final MajorMapper majorMapper;

    /**
     * 코드 기준 등록·갱신과 집계를 하나의 트랜잭션으로 수행한다.
     * 기존 ID와 원본에 없는 전공을 유지하며 저장 실패 시 전체 롤백한다.
     * @param source 트랜잭션 밖에서 검증·중복 제거된 원본
     * @return 등록·변경·동일 건수를 포함한 초기화 결과
     */
    @Transactional
    public MajorInitResDto initialize(MajorCsvReadResult source) {
        log.info("[MajorInitService] 전공 저장 | initialize() - START | totalCount: {}", source.rows().size());
        /* (1) 독립 트랜잭션의 동시 실행을 직렬화한 후 기존 데이터를 조회한다. */
        if (!Integer.valueOf(1).equals(majorRepository.acquireInitLock())) {
            throw new CustomException(MajorErrorCode.INIT_LOCK_UNAVAILABLE);
        }
        Map<String, Major> existing = majorRepository.findAll().stream()
                .collect(Collectors.toMap(Major::getCode, Function.identity()));

        /* (2) ID·code는 유지하고 분류·학과명·학위과정의 실제 변경만 반영한다. */
        List<Major> created = new ArrayList<>();
        int updatedCount = 0;
        int unchangedCount = 0;
        for (MajorCsvRow row : source.rows()) {
            Major incoming = majorMapper.toEntity(row);
            Major major = existing.get(row.code());
            if (major == null) {
                created.add(incoming);
            } else if (major.hasSameValues(incoming)) {
                unchangedCount++;
            } else {
                major.update(incoming);
                updatedCount++;
            }
        }
        /*
        * (3) 신규 INSERT와 변경 감지 UPDATE를 flush해 저장 오류를 전파한다.
        * - saveAll()은 엔티티를 그냥 저장하는데, SQL 실행이 flush나 커밋 시점까지 지연될 수 있기 때문입니다.
        * - saveAllAndFlush()는 저장 후 영속성 컨텍스트의 변경 사항 전체를 즉시 DB에 반영합니다.
        */
        majorRepository.saveAllAndFlush(created);
        MajorInitResDto result = MajorInitResDto.create(source.sourceRowCount(), source.duplicateRowCount(),
                source.rows().size(), created.size(), updatedCount, unchangedCount);
        log.info("[MajorInitService] 전공 저장 | initialize() - END | result: {}", result);
        return result;
    }
}
