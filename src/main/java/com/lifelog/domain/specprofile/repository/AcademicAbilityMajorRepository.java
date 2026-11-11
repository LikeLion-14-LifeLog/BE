package com.lifelog.domain.specprofile.repository;

import com.lifelog.domain.specprofile.entity.AcademicAbilityMajor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;

public interface AcademicAbilityMajorRepository extends JpaRepository<AcademicAbilityMajor, Long> {
    /** 학력 목록의 전공 연결을 조회한다. */
    @EntityGraph(attributePaths = "major")
    List<AcademicAbilityMajor> findAllByAcademicAbilityIdIn(List<Long> academicAbilityIds);
}

