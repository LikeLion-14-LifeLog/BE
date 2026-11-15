package com.lifelog.domain.specprofile.service.keep;

import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.dto.KeepSalaryPanelRecord.YearData;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.domain.specprofile.mapper.KeepSalaryCodeMapper;
import com.lifelog.domain.specprofile.service.keep.KeepSalaryConditionMatcher.*;
import com.lifelog.global.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
public class KeepSalaryRecordSelectionService {
    private final KeepSalaryCsvReader reader;
    private final KeepSalarySpecAccumulator accumulator;
    private final KeepSalaryConditionMatcher matcher;
    private final KeepSalaryCodeMapper codeMapper;
    private final ResourceLoader resources;
    private final String csvPath;

    /** 파일 어댑터와 순수 비교 로직을 주입한다. */
    public KeepSalaryRecordSelectionService(KeepSalaryCsvReader reader, KeepSalarySpecAccumulator accumulator,
                                           KeepSalaryConditionMatcher matcher, KeepSalaryCodeMapper codeMapper,
                                           ResourceLoader resources,
                                           @Value("${keep.salary.csv-path:classpath:data/KEEP2_YEARLY_SPEC_SALARY.csv}") String csvPath) {
        this.reader = reader;
        this.accumulator = accumulator;
        this.matcher = matcher;
        this.codeMapper = codeMapper;
        this.resources = resources;
        this.csvPath = csvPath;
    }

    /**
     * 트랜잭션 밖에서 Record당 한 연도를 선택하고 고유 패널 표본 10개 이상인지 검증한다.
     * @param conditions 구간 계산을 마친 사용자 조건
     * @return 입력 파일 순서의 불변 표본 목록
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<KeepSalaryMatchedSample> findBestSamples(SpecProfileSalaryClassificationData conditions) {
        log.info("[KeepSalaryRecordSelectionService] 표본 조회 | findBestSamples() - START");
        PreparedCriteria criteria = matcher.prepare(conditions, codeMapper.loadCodebook());
        List<KeepSalaryMatchedSample> result = new ArrayList<>();
        int[] inspected = {0};
        Map<String, Integer> excludedYears = new TreeMap<>();
        try (Reader input = new BufferedReader(new InputStreamReader(resources.getResource(csvPath).getInputStream(), StandardCharsets.UTF_8))) {
            reader.forEachRecord(input, panel -> {
                inspected[0]++;
                KeepSalaryMatchedSample selected = select(panel, criteria, excludedYears);
                if (selected != null) result.add(selected);
            });
        } catch (IOException exception) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.KEEP_CSV_READ_FAILED);
        }
        log.info("[KeepSalaryRecordSelectionService] 표본 조회 | findBestSamples() - END | inspected: {}, matched: {}, excluded: {}",
                inspected[0], result.size(), inspected[0] - result.size());
        log.info("[KeepSalaryRecordSelectionService] 연도별 후보 제외 사유 | excludedYears: {}", excludedYears);
        if (result.size() < 10) throw new CustomException(SpecProfileAnalyzationErrorCode.INSUFFICIENT_MATCHED_SAMPLES, Map.of("matchedSampleCount", result.size()));
        return List.copyOf(result);
    }

    /** 현재까지 누적된 값으로 필수 조건을 검사하고 유사도, 직접 일치 수, 최신 연도 순으로 선택한다. */
    private KeepSalaryMatchedSample select(KeepSalaryPanelRecord panel, PreparedCriteria criteria, Map<String, Integer> excludedYears) {
        log.debug("[KeepSalaryRecordSelectionService] 연도 선택 | select() - START");
        KeepSalaryAccumulatedSpec accumulated = null;
        KeepSalaryMatchedSample best = null;
        for (YearData current : panel.years()) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("연봉 표본 조회 중단");
            accumulated = accumulator.accumulate(accumulated, current);
            BigDecimal salary;
            try { salary = new BigDecimal(current.values().get("ANNUAL_SALARY_KRW")); }
            catch (NumberFormatException exception) {
                excludedYears.merge("MISSING_OR_INVALID_SALARY", 1, Integer::sum);
                continue;
            }
            if (salary.signum() <= 0) {
                excludedYears.merge("NON_POSITIVE_SALARY", 1, Integer::sum);
                continue;
            }
            MatchResult match = matcher.match(criteria, accumulated, current.values(), current.year());
            if (!match.eligible()) excludedYears.merge(match.rejectionReason(), 1, Integer::sum);
            if (match.eligible() && (best == null || match.similarityScore() > best.similarityScore()
                    || match.similarityScore() == best.similarityScore() && (match.directMatchCount() > best.directMatchCount()
                    || match.directMatchCount() == best.directMatchCount() && current.year() > best.selectedYear()))) {
                best = new KeepSalaryMatchedSample(panel.panelId(), current.year(), salary, accumulated,
                        match.similarityScore(), match.directMatchCount(), match.evidence());
            }
        }
        log.debug("[KeepSalaryRecordSelectionService] 연도 선택 | select() - END | matched: {}", best != null);
        return best;
    }
}
