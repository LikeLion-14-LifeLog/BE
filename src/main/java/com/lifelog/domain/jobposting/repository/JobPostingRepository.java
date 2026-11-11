package com.lifelog.domain.jobposting.repository;

import com.lifelog.domain.jobposting.entity.JobPosting;
import com.lifelog.domain.jobposting.entity.JobPostingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long>, JpaSpecificationExecutor<JobPosting> {

    List<JobPosting> findBySourceAndExternalIdIn(String source, Collection<String> externalIds);

    Optional<JobPosting> findBySourceAndExternalId(String source, String externalId);

    @Query("SELECT p.externalId FROM JobPosting p WHERE p.source = :source AND p.status = :status")
    List<String> findExternalIdsBySourceAndStatus(@Param("source") String source,
                                                  @Param("status") JobPostingStatus status);

    // 벌크 UPDATE는 @LastModifiedDate가 적용되지 않아 updatedAt을 직접 지정
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE JobPosting p
               SET p.status = :status, p.updatedAt = :now
             WHERE p.source = :source
               AND p.externalId IN :externalIds
               AND p.status <> :status
            """)
    int updateStatus(@Param("source") String source, @Param("externalIds") Collection<String> externalIds,
                     @Param("status") JobPostingStatus status, @Param("now") LocalDateTime now);
}
