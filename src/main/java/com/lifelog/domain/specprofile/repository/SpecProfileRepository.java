package com.lifelog.domain.specprofile.repository;

import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.enums.SpecProfileType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface SpecProfileRepository extends JpaRepository<SpecProfile, Long> {
    /** 회원의 유형별 프로필 개수를 조회한다. */
    long countByMemberIdAndType(Long memberId, SpecProfileType type);

    /** 프로필 개수 검증 후 기존 프로필을 조회한다. */
    List<SpecProfile> findAllByMemberIdAndType(Long memberId, SpecProfileType type);

    /** 동일 CURRENT에 대한 저장을 트랜잭션 종료까지 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SpecProfile p where p.member.id = :memberId and p.type = :type")
    List<SpecProfile> findForUpdate(@Param("memberId") Long memberId, @Param("type") SpecProfileType type);
}

