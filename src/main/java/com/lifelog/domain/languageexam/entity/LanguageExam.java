package com.lifelog.domain.languageexam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "language_exam")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LanguageExam {

    // CSV의 language_exam_id를 그대로 사용 (자동 생성 X)
    @Id
    @Column(name = "language_exam_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LanguageType languageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScoreType scoreType;

    @Builder
    private LanguageExam(Long id, String name, LanguageType languageType, ScoreType scoreType) {
        this.id = id;
        this.name = name;
        this.languageType = languageType;
        this.scoreType = scoreType;
    }

    public boolean hasSameValues(String name, LanguageType languageType, ScoreType scoreType) {
        return this.name.equals(name)
                && this.languageType == languageType
                && this.scoreType == scoreType;
    }

    public void update(String name, LanguageType languageType, ScoreType scoreType) {
        this.name = name;
        this.languageType = languageType;
        this.scoreType = scoreType;
    }
}
