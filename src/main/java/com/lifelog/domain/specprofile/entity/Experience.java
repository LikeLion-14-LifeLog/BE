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
@Table(name = "experience")
public class Experience extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "experience_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spec_profile_id", nullable = false)
    private SpecProfile specProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20)
    private ExperienceType type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "started_at")
    private LocalDate startedAt;

    @Column(name = "ended_at")
    private LocalDate endedAt;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "awarded", nullable = false)
    private boolean awarded;

    @Column(name = "organizer_name", length = 200)
    private String organizerName;

    @Column(name = "award_grade", length = 50)
    private String awardGrade;

    /** PK와 소유 프로필을 유지하고 경험 입력값을 덮어쓴다. */
    public void update(Experience source) {
        type = source.type;
        title = source.title;
        startedAt = source.startedAt;
        endedAt = source.endedAt;
        description = source.description;
        awarded = source.awarded;
        organizerName = source.organizerName;
        awardGrade = source.awardGrade;
    }

}

