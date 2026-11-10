package com.lifelog.domain.certification.service;
import com.lifelog.domain.certification.dto.*;
import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.certification.mapper.CertificationMapper;
import com.lifelog.domain.certification.repository.CertificationRepository;
import com.lifelog.domain.certification.exception.CertificationErrorCode;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificationInitService {
    private final CertificationRepository repository;
    private final CertificationMapper mapper;

    /** 전체 원본을 하나의 트랜잭션으로 반영한다. @param rows 검증된 원본 @return 처리 집계 */
    @Transactional
    public CertificationInitResDto initialize(List<CertificationCsvRow> rows) {
        log.info("[CertificationInitService] initialize() - START | count: {}", rows.size());
        /* (1) 빈 테이블에서도 동시 초기화를 직렬화한다. */
        if (!Integer.valueOf(1).equals(repository.acquireInitLock()))
            throw new CustomException(CertificationErrorCode.INIT_FAILED);
        var existing = repository.findAll();
        var byCode = existing.stream().filter(c -> c.getCode() != null)
                .collect(Collectors.toMap(Certification::getCode, Function.identity()));
        var byName = existing.stream().collect(Collectors.groupingBy(c -> List.of(c.getType().name(), c.getName())));
        Map<CertificationCsvRow, Certification> targets = new LinkedHashMap<>();
        Set<Long> selected = new HashSet<>();
        /* (2) 식별 모호성과 동일 행의 중복 갱신을 저장 전에 확인한다. */
        for (var row : rows) {
            Certification target = row.code() == null ? null : byCode.get(row.code());
            if (target == null) {
                var candidates = byName.getOrDefault(List.of(row.type().name(), row.name()), List.of()).stream()
                        .filter(c -> row.code() == null || c.getCode() == null).toList();
                if (candidates.size() > 1) throw new CustomException(CertificationErrorCode.IDENTITY_CONFLICT);
                if (!candidates.isEmpty()) target = candidates.getFirst();
            }
            if (target != null && !selected.add(target.getId()))
                throw new CustomException(CertificationErrorCode.IDENTITY_CONFLICT);
            targets.put(row, target);
        }
        /* (3) 기존 PK와 원본 밖 데이터를 유지하고 실제 변경만 반영한다. */
        List<Certification> created = new ArrayList<>();
        int updated = 0, unchanged = 0;
        for (var entry : targets.entrySet()) {
            var incoming = mapper.toEntity(entry.getKey());
            var current = entry.getValue();
            if (current == null) created.add(incoming);
            else if (current.hasSameValues(incoming)) unchanged++;
            else { current.update(incoming); updated++; }
        }
        repository.saveAllAndFlush(created);
        var result = CertificationInitResDto.create(rows.size(), created.size(), updated, unchanged);
        log.info("[CertificationInitService] initialize() - END | result: {}", result);
        return result;
    }
}
