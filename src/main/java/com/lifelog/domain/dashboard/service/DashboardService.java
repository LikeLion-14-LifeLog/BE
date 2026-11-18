package com.lifelog.domain.dashboard.service;

import com.lifelog.domain.dashboard.dto.DashboardResDto;
import com.lifelog.domain.dashboard.mapper.DashboardMapper;
import com.lifelog.domain.jobposting.dto.JobPostingListResponse;
import com.lifelog.domain.jobposting.entity.JobPosting;
import com.lifelog.domain.jobposting.entity.JobPostingSort;
import com.lifelog.domain.jobposting.repository.JobPostingRepository;
import com.lifelog.domain.jobposting.service.JobPostingService;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunResultResDto;
import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import com.lifelog.domain.specprofile.enums.SpecProfileType;
import com.lifelog.domain.specprofile.repository.SpecProfileAnalyzationRunRepository;
import com.lifelog.domain.specprofile.service.specprofileanalyzation.SpecProfileAnalyzationResultQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {
    private final SpecProfileAnalyzationRunRepository runRepository;
    private final SpecProfileAnalyzationResultQueryService resultQueryService;
    private final JobPostingService jobPostingService;
    private final JobPostingRepository jobPostingRepository;
    private final DashboardMapper mapper;

    /**
     * 본인 최신 분석과 현재 조건의 추천 공고를 같은 DB 스냅샷에서 읽는다.
     * @param memberId 인증 회원 ID
     * @return 최신 분석 상태와 추천 공고 최대 3개를 포함하는 대시보드 성공 응답
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardResDto getDashboard(Long memberId) {
        log.info("[DashboardService] 대시보드 조회 | getDashboard() - START | memberId: {}", memberId);
        /* (1) 상태와 관계없이 최신 미삭제 CURRENT 실행을 선택하고 기존 검증·복원 로직을 사용한다. */
        List<SpecProfileAnalyzationRun> latest = runRepository.findLatestOwnedRuns(memberId, SpecProfileType.CURRENT, PageRequest.of(0, 1));
        DashboardResDto.Analysis analysis = null;
        if (!latest.isEmpty()) {
            SpecProfileAnalyzationRun run = latest.getFirst();
            SpecProfileAnalyzationRunResultResDto runResult = resultQueryService.getRunResult(memberId, run.getId());
            analysis = mapper.toAnalysisResDto(run, runResult);
        }
        /* (2) 기존 맞춤 조건·마감 정렬·전체 건수를 그대로 적용한다. */
        JobPostingListResponse postings = jobPostingService.search(memberId, null, JobPostingSort.DEADLINE, 1, 3).content();
        /* (3) 선택된 공고만 요건까지 한 번에 읽고 Entity 접근이 끝난 DTO로 반환한다. */
        List<Long> ids = postings.jobPostings().stream().map(card -> card.jobPostingId()).toList();
        Map<Long, JobPosting> supplements = ids.isEmpty() ? Map.of()
                : jobPostingRepository.findAllWithRequirementsByIdIn(ids).stream().collect(Collectors.toMap(JobPosting::getId, Function.identity()));
        DashboardResDto result = mapper.toDashboardResDto(analysis, postings, supplements);
        log.info("[DashboardService] 대시보드 조회 | getDashboard() - END | memberId: {}, runId: {}, cards: {}",
                memberId, analysis == null ? null : analysis.runId(), postings.jobPostings().size());
        return result;
    }
}
