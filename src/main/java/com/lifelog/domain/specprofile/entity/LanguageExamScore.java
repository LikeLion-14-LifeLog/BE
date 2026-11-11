package com.lifelog.domain.specprofile.entity;

import jakarta.persistence.*;
import lombok.*;
import com.lifelog.global.entity.BaseTimeEntity;
import com.lifelog.domain.specprofile.enums.*;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.languageexam.entity.LanguageExam;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "language_exam_score")
public class LanguageExamScore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "language_exam_score_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spec_profile_id", nullable = false)
    private SpecProfile specProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "language_exam_id", nullable = false)
    private LanguageExam languageExam;

    @Column(name = "score_numeric", precision = 6, scale = 1)
    private BigDecimal scoreNumeric;

    @Column(name = "score_grade", length = 20)
    private String scoreGrade;

    @Column(name = "acquired_at", nullable = false)
    private LocalDate acquiredAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    /** PK와 소유 프로필을 유지하고 시험 및 성적 입력값을 덮어쓴다. */
    public void update(LanguageExamScore source) {
        languageExam = source.languageExam;
        scoreNumeric = source.scoreNumeric;
        scoreGrade = source.scoreGrade;
        acquiredAt = source.acquiredAt;
        expiresAt = source.expiresAt;
    }

}

