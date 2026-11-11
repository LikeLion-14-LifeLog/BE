package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.dto.JobPostingCardResponse;
import com.lifelog.domain.jobposting.dto.JobPostingDetailResponse;
import com.lifelog.domain.jobposting.exception.JobPostingErrorCode;
import com.lifelog.domain.jobposting.dto.JobPostingListResponse;
import com.lifelog.domain.jobposting.dto.JobPostingTabCounts;
import com.lifelog.domain.jobposting.entity.JobGroup;
import com.lifelog.domain.jobposting.entity.JobPosting;
import com.lifelog.domain.jobposting.entity.JobPostingRegion;
import com.lifelog.domain.jobposting.entity.JobPostingSort;
import com.lifelog.domain.jobposting.entity.JobPostingStatus;
import com.lifelog.domain.jobposting.repository.JobPostingBookmarkRepository;
import com.lifelog.domain.jobposting.repository.JobPostingRepository;
import com.lifelog.domain.jobposting.repository.JobPostingSpecification;
import com.lifelog.global.exception.CommonErrorCode;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.response.SliceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class JobPostingService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MAX_SIZE = 100;

    private final JobPostingRepository jobPostingRepository;
    private final JobPostingBookmarkRepository jobPostingBookmarkRepository;

    /**
     * 모집 중(OPEN)인 공고를 직무 그룹·지역으로 걸러 정렬·페이지네이션한다.
     * @param memberId 로그인 회원 ID (저장 여부·저장 수 계산), null이면 저장하지 않은 것으로 처리
     */
    @Transactional(readOnly = true)
    public SliceResponse<JobPostingListResponse> search(Long memberId, JobGroup jobGroup, JobPostingRegion region,
                                                        JobPostingSort sort, int page, int size) {
        if (page < 1) {
            throw new CustomException(CommonErrorCode.INVALID_PAGE);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new CustomException(CommonErrorCode.INVALID_SIZE);
        }

        LocalDate today = LocalDate.now(SEOUL);
        // Spring Data 페이지는 0부터 시작
        PageRequest pageable = PageRequest.of(page - 1, size, sort.toSort());
        Page<JobPosting> result = jobPostingRepository.findAll(
                JobPostingSpecification.search(JobPostingStatus.OPEN, today, jobGroup, region), pageable);

        Set<Long> bookmarkedIds = findBookmarkedIds(memberId, result.getContent());
        List<JobPostingCardResponse> cards = result.getContent().stream()
                .map(posting -> JobPostingCardResponse.of(posting, bookmarkedIds.contains(posting.getId()), today))
                .toList();

        JobPostingTabCounts tabCounts = new JobPostingTabCounts(
                // 필터 없는 목록과 같은 조건 (모집 중이고 마감일이 지나지 않은 공고)
                jobPostingRepository.count(JobPostingSpecification.search(JobPostingStatus.OPEN, today, null, null)),
                memberId == null ? 0 : jobPostingBookmarkRepository.countByMember_Id(memberId));

        return SliceResponse.of(result, new JobPostingListResponse(tabCounts, cards));
    }

    /**
     * 공고 상세. 상태(OPEN/CLOSE)·마감일과 관계없이 조회한다 (저장한 공고에서 들어올 수 있음).
     * @param memberId 로그인 회원 ID, null이면 저장하지 않은 것으로 처리
     */
    @Transactional(readOnly = true)
    public JobPostingDetailResponse getDetail(Long memberId, Long jobPostingId) {
        JobPosting posting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new CustomException(JobPostingErrorCode.JOB_POSTING_NOT_FOUND));
        boolean bookmarked = memberId != null
                && jobPostingBookmarkRepository.existsByMember_IdAndJobPosting_Id(memberId, jobPostingId);
        return JobPostingDetailResponse.of(posting, bookmarked, LocalDate.now(SEOUL));
    }

    private Set<Long> findBookmarkedIds(Long memberId, List<JobPosting> postings) {
        if (memberId == null || postings.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = postings.stream().map(JobPosting::getId).toList();
        return new HashSet<>(jobPostingBookmarkRepository.findBookmarkedJobPostingIds(memberId, ids));
    }
}
