package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.dto.JobPostingBookmarkResponse;
import com.lifelog.domain.jobposting.exception.JobPostingErrorCode;
import com.lifelog.domain.jobposting.repository.JobPostingBookmarkRepository;
import com.lifelog.domain.jobposting.repository.JobPostingRepository;
import com.lifelog.global.exception.CommonErrorCode;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobPostingBookmarkService {

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
