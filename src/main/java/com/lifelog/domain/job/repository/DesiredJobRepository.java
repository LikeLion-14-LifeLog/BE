package com.lifelog.domain.job.repository;

import com.lifelog.domain.job.entity.DesiredJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;

public interface DesiredJobRepository extends JpaRepository<DesiredJob, Long> {
    /** 회원의 단일 희망 직무를 조회한다. */
    @EntityGraph(attributePaths = "job")
    Optional<DesiredJob> findByMemberId(Long memberId);
}

