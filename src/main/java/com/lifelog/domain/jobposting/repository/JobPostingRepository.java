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

    /**
     * 대시보드에 선택된 공고의 마감 유형과 기술 요건을 한 번에 읽는다.
     * @param ids 기존 맞춤 조회가 선택한 공고 ID 목록
     * @return 요건 컬렉션까지 로딩한 공고 목록. 반환 순서는 카드 정렬에 사용하지 않는다
     */
    @Query("select distinct p from JobPosting p left join fetch p.requirements where p.id in :ids")
    List<JobPosting> findAllWithRequirementsByIdIn(@Param("ids") Collection<Long> ids);

    List<JobPosting> findBySourceAndExternalIdIn(String source, Collection<String> externalIds);

    Optional<JobPosting> findBySourceAndExternalId(String source, String externalId);

    @Query("SELECT p.externalId FROM JobPosting p WHERE p.source = :source AND p.status = :status")
    List<String> findExternalIdsBySourceAndStatus(@Param("source") String source,
                                                  @Param("status") JobPostingStatus status);

    // 저장 수는 읽고 쓰지 않고 DB에서 바로 증감 (동시 요청에도 누락 없음). updatedAt은 공고 내용 변경이 아니라 그대로 둠
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE JobPosting p SET p.bookmarkCount = p.bookmarkCount + 1 WHERE p.id = :id")
    int incrementBookmarkCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE JobPosting p SET p.bookmarkCount = p.bookmarkCount - 1 WHERE p.id = :id AND p.bookmarkCount > 0")
    int decrementBookmarkCount(@Param("id") Long id);

    @Query("SELECT p.bookmarkCount FROM JobPosting p WHERE p.id = :id")
    Optional<Integer> findBookmarkCount(@Param("id") Long id);

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
