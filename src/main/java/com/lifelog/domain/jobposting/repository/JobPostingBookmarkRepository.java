package com.lifelog.domain.jobposting.repository;

import com.lifelog.domain.jobposting.entity.JobPostingBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JobPostingBookmarkRepository extends JpaRepository<JobPostingBookmark, Long> {

    long countByMember_Id(Long memberId);

    boolean existsByMember_IdAndJobPosting_Id(Long memberId, Long jobPostingId);

    // 주어진 공고 중 회원이 저장한 공고 ID
    @Query("""
            SELECT b.jobPosting.id FROM JobPostingBookmark b
             WHERE b.member.id = :memberId
               AND b.jobPosting.id IN :jobPostingIds
            """)
    List<Long> findBookmarkedJobPostingIds(@Param("memberId") Long memberId,
                                           @Param("jobPostingIds") Collection<Long> jobPostingIds);

    /**
     * 이미 저장했으면 아무것도 하지 않는다. UNIQUE (member_id, job_posting_id) 충돌을 DB가 처리하므로
     * 동시 요청에도 한 건만 들어간다.
     * @return 새로 저장했으면 1, 이미 있었으면 0
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO job_posting_bookmark (member_id, job_posting_id, created_at)
            VALUES (:memberId, :jobPostingId, CURRENT_TIMESTAMP)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("memberId") Long memberId, @Param("jobPostingId") Long jobPostingId);

    /**
     * @return 삭제했으면 1, 저장하지 않았던 공고면 0
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM JobPostingBookmark b WHERE b.member.id = :memberId AND b.jobPosting.id = :jobPostingId")
    int deleteByMemberIdAndJobPostingId(@Param("memberId") Long memberId, @Param("jobPostingId") Long jobPostingId);
}
