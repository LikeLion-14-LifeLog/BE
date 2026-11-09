package com.lifelog.domain.major.repository;

import com.lifelog.domain.major.entity.Major;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MajorRepository extends JpaRepository<Major, Long> {

    /**
     * 전공이 비어 있어도 존재하는 단일 잠금 행으로 동시 초기화를 직렬화한다.
     * 호출 트랜잭션이 종료될 때까지 잠금을 유지한다.
     * @return 잠근 행의 ID (1)
     */
    @Query(value = "SELECT id FROM major_init_lock WHERE id = 1 FOR UPDATE", nativeQuery = true)
    Integer acquireInitLock();
}
