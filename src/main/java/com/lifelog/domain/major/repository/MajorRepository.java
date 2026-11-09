package com.lifelog.domain.major.repository;

import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.major.enums.MajorMainField;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MajorRepository extends JpaRepository<Major, Long> {

    /**
     * 제공된 검색 조건에 맞는 파생 메서드로 전공 페이지를 조회한다.
     * @param keyword 정규화한 학과명 검색어, null이면 이름 조건 생략
     * @param mainField 대계열, null이면 계열 조건 생략
     * @param pageable 정렬과 페이지 정보
     * @return 조건에 일치하는 전공과 전체 건수
     */
    default Page<Major> searchMajors(String keyword, MajorMainField mainField, Pageable pageable) {
        if (keyword == null) {
            return mainField == null ? findAll(pageable) : findByMainField(mainField, pageable);
        }
        return mainField == null
                ? findByNameContainingIgnoreCase(keyword, pageable)
                : findByNameContainingIgnoreCaseAndMainField(keyword, mainField, pageable);
    }

    /**
     * 학과명을 대소문자 구분 없이 부분 일치 검색한다. LIKE 특수문자는 문자 그대로 검색한다.
     * @param keyword 정규화한 학과명 검색어
     * @param pageable 정렬과 페이지 정보
     * @return 이름 조건에 일치하는 전공 페이지
     */
    Page<Major> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    /**
     * 대계열로 전공을 조회한다.
     * @param mainField 대계열
     * @param pageable 정렬과 페이지 정보
     * @return 계열 조건에 일치하는 전공 페이지
     */
    Page<Major> findByMainField(MajorMainField mainField, Pageable pageable);

    /**
     * 학과명 부분 일치와 대계열 조건을 AND로 적용한다.
     * @param keyword 정규화한 학과명 검색어, LIKE 특수문자는 문자 그대로 검색
     * @param mainField 대계열
     * @param pageable 정렬과 페이지 정보
     * @return 두 조건에 일치하는 전공 페이지
     */
    Page<Major> findByNameContainingIgnoreCaseAndMainField(
            String keyword, MajorMainField mainField, Pageable pageable);

    /**
     * 전공이 비어 있어도 존재하는 단일 잠금 행으로 동시 초기화를 직렬화한다.
     * 호출 트랜잭션이 종료될 때까지 잠금을 유지한다.
     * @return 잠근 행의 ID (1)
     */
    @Query(value = "SELECT id FROM major_init_lock WHERE id = 1 FOR UPDATE", nativeQuery = true)
    Integer acquireInitLock();
}
