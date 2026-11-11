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
@Table(name = "academic_ability_major")
public class AcademicAbilityMajor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "academic_ability_major_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_ability_id", nullable = false)
    private AcademicAbility academicAbility;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "major_id", nullable = false)
    private Major major;

    /** 학력 연결의 PK를 유지하고 전공을 변경한다. */
    public void changeMajor(Major major) {
        this.major = major;
    }

}

