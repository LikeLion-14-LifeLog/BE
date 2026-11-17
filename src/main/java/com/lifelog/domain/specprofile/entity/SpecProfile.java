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
@Table(name = "spec_profile", indexes = @Index(name = "idx_spec_profile_member_type_selected",
        columnList = "member_id,type,target_spec_selected"))
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

    @Column(name = "target_spec_selected")
    private Boolean targetSpecSelected;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_company_group_scale", length = 20)
    private CompanyGroupScale targetCompanyGroupScale;

    @Column(name = "target_conditions_recorded", nullable = false)
    private boolean targetConditionsRecorded;

    /** 추가 목표 구분이 모두 저장된 프로필의 조회 조건을 기록한다. */
    public void recordTargetConditions(CompanyGroupScale companyGroupScale) {
        this.targetCompanyGroupScale = companyGroupScale;
        this.targetConditionsRecorded = true;
    }

    /** 다른 사용자 목표를 선택할 때 이전 선택을 해제한다. */
    public void deselectTarget() {
        this.targetSpecSelected = false;
    }

    /** 기본 정보만 갱신하고 프로필 식별 정보는 유지한다. */
    public void updateTemporaryInfo(Integer birthYear, ResidentialArea residenceRegion) {
        this.birthYear = birthYear.shortValue();
        this.residenceRegion = residenceRegion;
    }

    /** 사용자 입력 목표를 1번 TARGET 슬롯에 적용한다. */
    public void applyUserTarget(SpecProfile base) {
        this.type = SpecProfileType.TARGET;
        this.baseSpecProfile = base;
        this.sortOrder = 1;
        this.targetSpecSelected = true;
        this.birthYear = base.birthYear;
        this.residenceRegion = base.residenceRegion;
    }

}

