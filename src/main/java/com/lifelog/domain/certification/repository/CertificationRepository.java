package com.lifelog.domain.certification.repository;
import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.certification.enums.CertificationType;
import com.lifelog.domain.certification.enums.CertificationMainCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface CertificationRepository extends JpaRepository<Certification, Long> {
    /**
     * 전달된 선택 조건만 AND로 적용해 자격증 페이지를 조회한다.
     * @param keyword 정규화된 이름 검색어, null이면 조건 생략
     * @param type 자격증 유형, null이면 조건 생략
     * @param mainCategory 대분류, null이면 조건 생략
     * @param pageable 정렬과 페이지 정보
     * @return 조건에 맞는 목록과 전체 건수
     */
    default Page<Certification> searchCertifications(String keyword, CertificationType type,
                                                    CertificationMainCategory mainCategory, Pageable pageable) {
        if (keyword == null) {
            if (type == null) {
                return mainCategory == null ? findAll(pageable) : findByMainCategory(mainCategory, pageable);
            }
            return mainCategory == null ? findByType(type, pageable)
                    : findByTypeAndMainCategory(type, mainCategory, pageable);
        }
        if (type == null) {
            return mainCategory == null ? findByNameContainingIgnoreCase(keyword, pageable)
                    : findByNameContainingIgnoreCaseAndMainCategory(keyword, mainCategory, pageable);
        }
        return mainCategory == null ? findByNameContainingIgnoreCaseAndType(keyword, type, pageable)
                : findByNameContainingIgnoreCaseAndTypeAndMainCategory(keyword, type, mainCategory, pageable);
    }

    /**
     * 이름을 대소문자 구분 없이 부분 검색하며 LIKE 특수문자는 문자 그대로 처리한다.
     * @param keyword 이름 검색어
     * @param pageable 정렬과 페이지 정보
     * @return 이름에 일치하는 페이지
     */
    Page<Certification> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    /**
     * 유형으로 조회한다.
     * @param type 자격증 유형
     * @param pageable 정렬과 페이지 정보
     * @return 유형에 일치하는 페이지
     */
    Page<Certification> findByType(CertificationType type, Pageable pageable);

    /**
     * 대분류로 조회한다.
     * @param mainCategory 자격증 대분류
     * @param pageable 정렬과 페이지 정보
     * @return 대분류에 일치하는 페이지
     */
    Page<Certification> findByMainCategory(CertificationMainCategory mainCategory, Pageable pageable);

    /**
     * 유형과 대분류를 AND로 조회한다.
     * @param type 자격증 유형
     * @param mainCategory 자격증 대분류
     * @param pageable 정렬과 페이지 정보
     * @return 두 조건에 일치하는 페이지
     */
    Page<Certification> findByTypeAndMainCategory(CertificationType type,
                                                CertificationMainCategory mainCategory, Pageable pageable);

    /**
     * 이름 부분 검색과 유형을 AND로 조회한다.
     * @param keyword 이름 검색어
     * @param type 자격증 유형
     * @param pageable 정렬과 페이지 정보
     * @return 두 조건에 일치하는 페이지
     */
    Page<Certification> findByNameContainingIgnoreCaseAndType(String keyword, CertificationType type, Pageable pageable);

    /**
     * 이름 부분 검색과 대분류를 AND로 조회한다.
     * @param keyword 이름 검색어
     * @param mainCategory 자격증 대분류
     * @param pageable 정렬과 페이지 정보
     * @return 두 조건에 일치하는 페이지
     */
    Page<Certification> findByNameContainingIgnoreCaseAndMainCategory(
            String keyword, CertificationMainCategory mainCategory, Pageable pageable);

    /**
     * 이름 부분 검색, 유형, 대분류를 AND로 조회한다.
     * @param keyword 이름 검색어
     * @param type 자격증 유형
     * @param mainCategory 자격증 대분류
     * @param pageable 정렬과 페이지 정보
     * @return 세 조건에 일치하는 페이지
     */
    Page<Certification> findByNameContainingIgnoreCaseAndTypeAndMainCategory(
            String keyword, CertificationType type, CertificationMainCategory mainCategory, Pageable pageable);

    /** 빈 테이블에서도 초기화를 직렬화한다. @return 잠금 행 ID */
    @Query(value = "SELECT id FROM certification_init_lock WHERE id = 1 FOR UPDATE", nativeQuery = true)
    Integer acquireInitLock();
}
