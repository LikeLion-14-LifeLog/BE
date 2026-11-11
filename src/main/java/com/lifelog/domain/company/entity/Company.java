package com.lifelog.domain.company.entity;

import com.lifelog.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "company")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_id")
    private Long id;

    // 고유 키가 없어 name 기준으로 조회 (UNIQUE 아님)
    @Column(nullable = false, length = 200)
    private String name;

    // 서울 일자리포털은 업종 코드를 주지 않아 null
    @Column(length = 20)
    private String industryCode;

    @Column(length = 100)
    private String industryName;

    // SMALL | MEDIUM | LARGE 등, 원본에 없어 현재는 null
    @Column(length = 20)
    private String sizeType;

    @Column(length = 500)
    private String homepageUrl;

    @Builder
    private Company(String name, String industryCode, String industryName,
                    String sizeType, String homepageUrl) {
        this.name = name;
        this.industryCode = industryCode;
        this.industryName = industryName;
        this.sizeType = sizeType;
        this.homepageUrl = homepageUrl;
    }
}
