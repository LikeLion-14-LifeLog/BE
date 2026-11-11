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
@Table(name = "academic_ability")
public class AcademicAbility extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "academic_ability_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spec_profile_id", nullable = false)
    private SpecProfile specProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 50)
    private AcademicAbilityType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private AcademicAbilityStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "location", length = 20)
    private ResidentialArea location;

    @Column(name = "university_grade", precision = 3, scale = 2)
    private BigDecimal universityGrade;

    @Column(name = "university_standard_grade", precision = 5, scale = 1)
    private BigDecimal universityStandardGrade;

    @Column(name = "graduated_at")
    private LocalDate graduatedAt;

    /** PK와 소유 프로필을 유지하고 학력 입력값을 덮어쓴다. */
    public void update(AcademicAbility source) {
        type = source.type;
        status = source.status;
        location = source.location;
        universityGrade = source.universityGrade;
        universityStandardGrade = source.universityStandardGrade;
        graduatedAt = source.graduatedAt;
    }

}

