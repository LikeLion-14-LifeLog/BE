package com.lifelog.domain.job.entity;

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

import java.util.Objects;

@Getter
@Entity
@Table(name = "job")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long id;

    // 미분류(9999)는 대분류 코드와 중·소분류가 null
    @Column(length = 1)
    private String mainCategoryCode;

    @Column(nullable = false, length = 100)
    private String mainCategoryName;

    @Column(length = 2)
    private String middleCategoryCode;

    @Column(length = 100)
    private String middleCategoryName;

    @Column(length = 3)
    private String subCategoryCode;

    @Column(length = 100)
    private String subCategoryName;

    // 8차 고용직업분류 세분류 코드 (앞자리 0 유지)
    @Column(nullable = false, unique = true, length = 4)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Builder
    private Job(String mainCategoryCode, String mainCategoryName,
               String middleCategoryCode, String middleCategoryName,
               String subCategoryCode, String subCategoryName,
               String code, String name) {
        this.mainCategoryCode = mainCategoryCode;
        this.mainCategoryName = mainCategoryName;
        this.middleCategoryCode = middleCategoryCode;
        this.middleCategoryName = middleCategoryName;
        this.subCategoryCode = subCategoryCode;
        this.subCategoryName = subCategoryName;
        this.code = code;
        this.name = name;
    }

    public boolean hasSameValues(Job other) {
        return Objects.equals(mainCategoryCode, other.mainCategoryCode)
                && mainCategoryName.equals(other.mainCategoryName)
                && Objects.equals(middleCategoryCode, other.middleCategoryCode)
                && Objects.equals(middleCategoryName, other.middleCategoryName)
                && Objects.equals(subCategoryCode, other.subCategoryCode)
                && Objects.equals(subCategoryName, other.subCategoryName)
                && name.equals(other.name);
    }

    // code는 upsert 기준이라 변경하지 않음
    public void update(Job source) {
        this.mainCategoryCode = source.mainCategoryCode;
        this.mainCategoryName = source.mainCategoryName;
        this.middleCategoryCode = source.middleCategoryCode;
        this.middleCategoryName = source.middleCategoryName;
        this.subCategoryCode = source.subCategoryCode;
        this.subCategoryName = source.subCategoryName;
        this.name = source.name;
    }
}
