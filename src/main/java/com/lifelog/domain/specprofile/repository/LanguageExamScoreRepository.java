package com.lifelog.domain.specprofile.repository;

import com.lifelog.domain.specprofile.entity.LanguageExamScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;

public interface LanguageExamScoreRepository extends JpaRepository<LanguageExamScore, Long> {
    /** 지정한 프로필의 항목만 조회한다. */
    @EntityGraph(attributePaths = "languageExam")
    List<LanguageExamScore> findAllBySpecProfileId(Long specProfileId);
}

