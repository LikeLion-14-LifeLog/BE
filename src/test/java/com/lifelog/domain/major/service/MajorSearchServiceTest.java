package com.lifelog.domain.major.service;

import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.major.enums.DegreeProcess;
import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.domain.major.enums.MajorMiddleField;
import com.lifelog.domain.major.enums.MajorSubField;
import com.lifelog.domain.major.exception.MajorErrorCode;
import com.lifelog.domain.major.repository.MajorRepository;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.exception.CommonErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = MajorInitServiceTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MajorSearchServiceTest {
    @Autowired private MajorService service;
    @Autowired private MajorRepository repository;
    @Autowired private DataSource dataSource;

    @BeforeAll
    void prepareDatabase() throws Exception {
        MajorTestDatabaseSupport.initialize(dataSource);
    }

    @AfterEach
    void cleanUp() {
        repository.deleteAllInBatch();
    }

    @Test
    @DisplayName("앞뒤 공백을 제거하여 조회하고 한글 분류명과 학위과정을 매핑한다")
    void 검색어_정규화_응답매핑_성공() {
        // given
        Major saved = saveMajor("컴퓨터공학과");
        // when
        var response = service.searchMajors("  컴퓨터공학  ", MajorMainField.ENGINEERING, 1, 20);
        // then
        assertThat(response.totalElements()).isEqualTo(1);
        var major = response.content().majors().getFirst();
        assertThat(major.majorId()).isEqualTo(saved.getId());
        assertThat(major.mainField()).isEqualTo(MajorMainField.ENGINEERING);
        assertThat(major.middleField()).isEqualTo("컴퓨터ㆍ통신");
        assertThat(major.subField()).isEqualTo("전산학ㆍ컴퓨터공학");
        assertThat(major.name()).isEqualTo("컴퓨터공학과");
        assertThat(major.code()).isEqualTo("U01");
        assertThat(major.degreeProcessName()).isEqualTo(DegreeProcess.UNIVERSITY);
    }

    @Test
    @DisplayName("누락되거나 공백뿐인 검색어는 전체 목록을 조회한다")
    void 공백_전체조회_성공() {
        // given
        saveMajor("컴퓨터공학과");
        // when / then
        for (String keyword : new String[]{null, "", " \t\n", " ".repeat(101)}) {
            assertThat(service.searchMajors(keyword, null, 1, 100).totalElements()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("검색어 길이는 앞뒤 공백 제거 후 100자까지 허용한다")
    void 검색어_길이_경계값_성공() {
        // given
        saveMajor("가".repeat(100));
        // when / then
        assertThat(service.searchMajors(" " + "가".repeat(100) + " ", null, 1, 1).totalElements()).isEqualTo(1);
        assertThatThrownBy(() -> service.searchMajors("가".repeat(101), null, 1, 20))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MajorErrorCode.INVALID_KEYWORD));
    }

    @Test
    @DisplayName("페이지 번호와 크기가 허용 범위를 벗어나면 공통 오류를 반환한다")
    void 페이지_검증_실패_범위초과() {
        // given / when / then
        assertThatThrownBy(() -> service.searchMajors(null, null, 0, 20))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PAGE));
        for (int size : new int[]{0, 101}) {
            assertThatThrownBy(() -> service.searchMajors(null, null, 1, size))
                    .isInstanceOfSatisfying(CustomException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_SIZE));
        }
    }

    private Major saveMajor(String name) {
        return repository.saveAndFlush(Major.builder().code("U01").name(name).mainField(MajorMainField.ENGINEERING)
                .middleField(MajorMiddleField.COMPUTER_COMMUNICATIONS)
                .subField(MajorSubField.COMPUTER_SCIENCE_ENGINEERING).degreeProcess(DegreeProcess.UNIVERSITY).build());
    }
}
