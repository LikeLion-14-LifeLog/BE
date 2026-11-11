package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.dto.JobPostingBookmarkListResponse;
import com.lifelog.domain.jobposting.dto.JobPostingBookmarkResponse;
import com.lifelog.domain.jobposting.dto.JobPostingCardResponse;
import com.lifelog.domain.jobposting.entity.JobPostingBookmark;
import com.lifelog.domain.jobposting.exception.JobPostingErrorCode;
import com.lifelog.domain.jobposting.repository.JobPostingBookmarkRepository;
import com.lifelog.domain.jobposting.repository.JobPostingRepository;
import com.lifelog.global.exception.CommonErrorCode;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.response.SliceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobPostingBookmarkService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MAX_SIZE = 100;

    private final JobPostingRepository jobPostingRepository;
    private final JobPostingBookmarkRepository jobPostingBookmarkRepository;

    /**
     * 공고 저장. 이미 저장했으면 아무것도 바꾸지 않고 성공 (멱등).
     * 실제로 새로 저장된 경우에만 bookmark_count를 1 올린다. 마감된 공고도 저장할 수 있다.
     */
    @Transactional
    public JobPostingBookmarkResponse bookmark(Long memberId, Long jobPostingId) {
        validate(memberId, jobPostingId);
        if (jobPostingBookmarkRepository.insertIfAbsent(memberId, jobPostingId) == 1) {
            jobPostingRepository.incrementBookmarkCount(jobPostingId);
        }
        return response(jobPostingId, true);
    }

    /**
     * 저장 취소. 저장하지 않은 공고여도 성공 (멱등).
     * 실제로 삭제된 경우에만 bookmark_count를 1 내린다 (0 아래로는 내려가지 않음).
     */
    @Transactional
    public JobPostingBookmarkResponse unbookmark(Long memberId, Long jobPostingId) {
        validate(memberId, jobPostingId);
        if (jobPostingBookmarkRepository.deleteByMemberIdAndJobPostingId(memberId, jobPostingId) == 1) {
            jobPostingRepository.decrementBookmarkCount(jobPostingId);
        }
        return response(jobPostingId, false);
    }

    /**
     * 내가 저장한 공고 목록 (최근 저장 순). 마감된 공고도 포함한다.
     */
    @Transactional(readOnly = true)
    public SliceResponse<JobPostingBookmarkListResponse> getBookmarks(Long memberId, int page, int size) {
        if (memberId == null) {
            throw new CustomException(CommonErrorCode.UNAUTHORIZED);
        }
        if (page < 1) {
            throw new CustomException(CommonErrorCode.INVALID_PAGE);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new CustomException(CommonErrorCode.INVALID_SIZE);
        }

        // Spring Data 페이지는 0부터 시작. 같은 시각에 저장했으면 나중에 저장한 것(ID 큰 것) 먼저
        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<JobPostingBookmark> result = jobPostingBookmarkRepository.findByMember_Id(memberId, pageable);

        LocalDate today = LocalDate.now(SEOUL);
        List<JobPostingCardResponse> cards = result.getContent().stream()
                .map(bookmark -> JobPostingCardResponse.of(bookmark.getJobPosting(), true, today))
                .toList();
        return SliceResponse.of(result, new JobPostingBookmarkListResponse(cards));
    }

    private void validate(Long memberId, Long jobPostingId) {
        if (memberId == null) {
            throw new CustomException(CommonErrorCode.UNAUTHORIZED);
        }
        if (!jobPostingRepository.existsById(jobPostingId)) {
            throw new CustomException(JobPostingErrorCode.JOB_POSTING_NOT_FOUND);
        }
    }

    private JobPostingBookmarkResponse response(Long jobPostingId, boolean bookmarked) {
        int bookmarkCount = jobPostingRepository.findBookmarkCount(jobPostingId)
                .orElseThrow(() -> new CustomException(JobPostingErrorCode.JOB_POSTING_NOT_FOUND));
        return new JobPostingBookmarkResponse(jobPostingId, bookmarked, bookmarkCount);
    }
}
