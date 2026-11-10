package com.lifelog.domain.certification.repository;
import com.lifelog.domain.certification.entity.Certification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface CertificationRepository extends JpaRepository<Certification, Long> {
    /** 빈 테이블에서도 초기화를 직렬화한다. @return 잠금 행 ID */
    @Query(value = "SELECT id FROM certification_init_lock WHERE id = 1 FOR UPDATE", nativeQuery = true)
    Integer acquireInitLock();
}
