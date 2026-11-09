package com.lifelog.domain.major.repository;

import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.major.enums.DegreeProcess;
import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.domain.major.enums.MajorMiddleField;
import com.lifelog.domain.major.enums.MajorSubField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"})
@ContextConfiguration(classes = MajorRepositoryTest.TestConfiguration.class)
class MajorRepositoryTest {
    @Autowired private MajorRepository repository;

    @BeforeEach
    void setUp() {
        repository.save(major("C03", "컴퓨터공학과", MajorMainField.ENGINEERING));
        repository.save(major("C01", "컴퓨터공학과", MajorMainField.ENGINEERING));
        repository.save(major("C02", "Computer Science", MajorMainField.NATURAL_SCIENCES));
        repository.save(major("C04", "100%_\\학과", MajorMainField.HUMANITIES));
        repository.flush();
    }

    @Test
    @DisplayName("이름이 같아도 다른 코드는 보존하고 코드 순으로 페이지를 조회한다")
    void 동일이름_다른코드_페이징_성공() {
        // given
        var pageable = PageRequest.of(0, 1, Sort.by("code"));
        // when
        var result = repository.searchMajors("컴퓨터", MajorMainField.ENGINEERING, pageable);
        // then
        assertThat(result.getContent()).extracting(Major::getCode).containsExactly("C01");
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    @DisplayName("영문 대소문자를 구분하지 않고 이름을 부분 일치 검색한다")
    void 영문_부분일치_성공() {
        // given / when
        var result = repository.searchMajors("pUtEr", null, PageRequest.of(0, 20));
        // then
        assertThat(result.getContent()).extracting(Major::getCode).containsExactly("C02");
        assertThat(repository.searchMajors("pUtEr", MajorMainField.ENGINEERING, PageRequest.of(0, 20))
                .getContent()).isEmpty();
    }

    @Test
    @DisplayName("누락된 조건은 전체 조회하고 계열만 있으면 해당 계열만 반환한다")
    void 선택조건_생략_성공() {
        // given / when / then
        assertThat(repository.searchMajors(null, null, PageRequest.of(0, 20)).getContent()).hasSize(4);
        assertThat(repository.searchMajors(null, MajorMainField.ENGINEERING, PageRequest.of(0, 20))
                .getContent()).hasSize(2);
    }

    @Test
    @DisplayName("퍼센트 밑줄 역슬래시는 와일드카드가 아닌 문자로 검색한다")
    void LIKE_특수문자_검색_성공() {
        // given / when / then
        for (String keyword : new String[]{"%", "_", "\\", "%_\\"}) {
            assertThat(repository.searchMajors(keyword, null, PageRequest.of(0, 20)).getContent())
                    .extracting(Major::getCode).containsExactly("C04");
        }
    }

    @Test
    @DisplayName("마지막 다음 페이지는 전체 집계를 유지하고 빈 목록을 반환한다")
    void 범위초과_페이지_성공() {
        // given / when
        var result = repository.searchMajors(null, null, PageRequest.of(4, 2));
        // then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isFalse();
    }

    private Major major(String code, String name, MajorMainField mainField) {
        return Major.builder().code(code).name(name).mainField(mainField)
                .middleField(MajorMiddleField.COMPUTER_COMMUNICATIONS)
                .subField(MajorSubField.COMPUTER_SCIENCE_ENGINEERING).degreeProcess(DegreeProcess.UNIVERSITY).build();
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = Major.class)
    @EnableJpaRepositories(basePackageClasses = MajorRepository.class)
    static class TestConfiguration {
    }
}
