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
@Table(name = "spec_profile")
public class SpecProfile extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "spec_profile_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20)
    @Builder.Default
    private SpecProfileType type = SpecProfileType.CURRENT;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "birth_year")
    private Short birthYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "residence_region", length = 20)
    private ResidentialArea residenceRegion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_spec_profile_id")
    private SpecProfile baseSpecProfile;

    /** 기본 정보만 갱신하고 프로필 식별 정보는 유지한다. */
    public void updateTemporaryInfo(Integer birthYear, ResidentialArea residenceRegion) {
        this.birthYear = birthYear.shortValue();
        this.residenceRegion = residenceRegion;
    }

}

