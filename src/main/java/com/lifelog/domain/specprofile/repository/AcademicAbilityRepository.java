package com.lifelog.domain.specprofile.repository;

import com.lifelog.domain.specprofile.entity.AcademicAbility;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AcademicAbilityRepository extends JpaRepository<AcademicAbility, Long> {
    /** 지정한 프로필의 항목만 조회한다. */
    List<AcademicAbility> findAllBySpecProfileId(Long specProfileId);
}

