package com.lifelog.domain.specprofile.repository;

import com.lifelog.domain.specprofile.entity.SpecProfileAnalyzationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import com.lifelog.domain.specprofile.enums.SpecProfileType;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface SpecProfileAnalyzationRunRepository extends JpaRepository<SpecProfileAnalyzationRun, Long> {
    /**
     * 인증 회원의 미삭제 실행 중 허용된 상태에 해당하는 최신 실행을 조회한다.
     * @param memberId 인증 회원 ID
     * @param statuses 조회를 허용할 실행 상태
     * @return 요청 시각과 PK 내림차순의 첫 실행 또는 빈 값
     */
    Optional<SpecProfileAnalyzationRun> findFirstBySpecProfileMemberIdAndStatusInAndDeleteAtIsNullOrderByCreatedAtDescIdDesc(
            Long memberId, Collection<SpecProfileAnalyzationStatus> statuses);

    /**
     * 대시보드에서 본인 프로필의 미삭제 실행을 최신 요청 시각과 PK 순서로 조회한다.
     * @param memberId 인증 회원 ID
     * @param type 조회할 프로필 유형
     * @param pageable 조회 범위. 대시보드는 첫 실행 한 건만 요청한다
     * @return 상태와 관계없는 최신 실행 목록
     */
    @Query("""
            select r from SpecProfileAnalyzationRun r join fetch r.specProfile p
            where p.member.id = :memberId and p.type = :type and r.deleteAt is null
            order by r.createdAt desc, r.id desc
            """)
    List<SpecProfileAnalyzationRun> findLatestOwnedRuns(@Param("memberId") Long memberId,
            @Param("type") SpecProfileType type, Pageable pageable);

    /**
     * 인증 회원 소유인 실행과 해당 프로필을 조회한다.
     * @param runId 실행 ID
     * @param memberId 인증 회원 ID
     * @return 본인 실행 또는 빈 값
     */
    @Query("select r from SpecProfileAnalyzationRun r join fetch r.specProfile p where r.id = :runId and p.member.id = :memberId")
    Optional<SpecProfileAnalyzationRun> findOwnedRun(@Param("runId") Long runId, @Param("memberId") Long memberId);

    /**
     * 소유권 조건을 충족하는 실행 행만 잠가 종료 상태 전환을 직렬화한다.
     * @param runId 실행 ID
     * @param memberId 인증 회원 ID
     * @return 잠금 상태의 본인 실행 또는 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from SpecProfileAnalyzationRun r
            where r.id = :runId and exists (
                select p.id from SpecProfile p
                where p.id = r.specProfile.id and p.member.id = :memberId
            )
            """)
    Optional<SpecProfileAnalyzationRun> findOwnedRunForUpdate(@Param("runId") Long runId, @Param("memberId") Long memberId);

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

    /**
     * 가장 최근에 완료한 CURRENT 분석을 조회한다. 추천 처리에서는 기존 결과를 읽기만 한다.
     * @param specProfileId 본인 CURRENT ID
     * @param status 완료 상태
     * @return 최신 완료 실행
     */
    Optional<SpecProfileAnalyzationRun> findFirstBySpecProfileIdAndStatusOrderByCreatedAtDescIdDesc(
            Long specProfileId, SpecProfileAnalyzationStatus status);
}
