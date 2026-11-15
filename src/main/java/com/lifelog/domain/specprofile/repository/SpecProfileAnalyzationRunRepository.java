package com.lifelog.domain.specprofile.repository;

import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import java.util.Optional;
import java.util.Collection;

public interface SpecProfileAnalyzationRunRepository extends JpaRepository<SpecProfileAnalyzationRun, Long> {
    /**
     * 인증 회원 소유인 실행과 해당 프로필을 조회한다.
     * @param runId 실행 ID
     * @param memberId 인증 회원 ID
     * @return 본인 실행 또는 빈 값
     */
    @Query("select r from SpecProfileAnalyzationRun r join fetch r.specProfile p where r.id = :runId and p.member.id = :memberId")
    Optional<SpecProfileAnalyzationRun> findOwnedRun(@Param("runId") Long runId, @Param("memberId") Long memberId);

    /**
     * 결과 저장·완료와 취소·실패 전환이 동시에 실행되지 않도록 실행 행을 잠근다.
     * @param runId 실행 ID
     * @return 잠금 상태의 실행 또는 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SpecProfileAnalyzationRun r where r.id = :runId")
    Optional<SpecProfileAnalyzationRun> findByIdForUpdate(@Param("runId") Long runId);

    /**
     * 예상 상태가 일치하는 실행만 원자적으로 다음 상태로 변경한다.
     * @param runId 실행 ID
     * @param expected 변경 전 상태
     * @param next 변경할 상태
     * @return 변경된 행 수
     */
    @Modifying
    @Query("update SpecProfileAnalyzationRun r set r.status = :next where r.id = :runId and r.status = :expected")
    int transitionStatus(@Param("runId") Long runId, @Param("expected") SpecProfileAnalyzationStatus expected,
                         @Param("next") SpecProfileAnalyzationStatus next);

    /**
     * 변경 가능한 상태 중 하나인 실행만 원자적으로 다음 상태로 전환한다.
     * @param runId 실행 ID
     * @param expected 변경을 허용할 현재 상태들
     * @param next 변경할 상태
     * @return 변경된 행 수
     */
    @Modifying
    @Query("update SpecProfileAnalyzationRun r set r.status = :next where r.id = :runId and r.status in :expected")
    int transitionStatuses(@Param("runId") Long runId, @Param("expected") Collection<SpecProfileAnalyzationStatus> expected,
                           @Param("next") SpecProfileAnalyzationStatus next);

    /**
     * 지연 로딩 Entity 없이 저장된 스냅샷 문자열만 조회한다.
     * @param runId 실행 ID
     * @return 스냅샷 JSON
     */
    @Query("select r.specProfileSnapshotJson from SpecProfileAnalyzationRun r where r.id = :runId")
    Optional<String> findSnapshotJsonById(@Param("runId") Long runId);

    /**
     * 프로필의 최신 실행을 요청 시각과 PK 역순으로 조회한다.
     * @param specProfileId 잠금 상태인 스펙 프로필 ID
     * @return 최신 실행 또는 빈 값
     */
    Optional<SpecProfileAnalyzationRun> findFirstBySpecProfileIdOrderByCreatedAtDescIdDesc(Long specProfileId);
}
