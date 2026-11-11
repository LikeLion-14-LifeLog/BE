package com.lifelog.domain.jobposting.repository;

import com.lifelog.domain.jobposting.entity.JobPostingBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JobPostingBookmarkRepository extends JpaRepository<JobPostingBookmark, Long> {

    long countByMember_Id(Long memberId);

    // 주어진 공고 중 회원이 저장한 공고 ID
    @Query("""
            SELECT b.jobPosting.id FROM JobPostingBookmark b
             WHERE b.member.id = :memberId
               AND b.jobPosting.id IN :jobPostingIds
            """)
    List<Long> findBookmarkedJobPostingIds(@Param("memberId") Long memberId,
                                           @Param("jobPostingIds") Collection<Long> jobPostingIds);
}
