package com.lifelog.domain.specprofile.repository;

import com.lifelog.domain.specprofile.entity.Experience;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {
    /** 지정한 프로필의 항목만 조회한다. */
    List<Experience> findAllBySpecProfileId(Long specProfileId);
}

