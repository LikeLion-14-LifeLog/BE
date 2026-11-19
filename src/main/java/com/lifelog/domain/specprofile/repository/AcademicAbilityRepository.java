package com.lifelog.domain.specprofile.repository;

import com.lifelog.domain.specprofile.entity.AcademicAbility;
import com.lifelog.domain.specprofile.enums.AcademicAbilityType;
import com.lifelog.domain.specprofile.enums.SpecProfileType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AcademicAbilityRepository extends JpaRepository<AcademicAbility, Long> {
    /** 지정한 프로필의 항목만 조회한다. */
    List<AcademicAbility> findAllBySpecProfileId(Long specProfileId);

    /**
     * 회원의 지정된 프로필 유형에서 중복 없는 학력 유형을 조회한다.
     * @param memberId 인증된 회원 ID
     * @param profileType 조회할 프로필 유형
     * @return 저장된 학력 유형 목록
     */
    @Query("select distinct a.type from AcademicAbility a where a.specProfile.member.id = :memberId "
            + "and a.specProfile.type = :profileType and a.type is not null")
    List<AcademicAbilityType> findDistinctTypesByMemberIdAndProfileType(
            @Param("memberId") Long memberId, @Param("profileType") SpecProfileType profileType);
}

