package com.lifelog.domain.major.service;

import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.major.dto.MajorListResDto;
import com.lifelog.domain.major.dto.MajorResDto;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.domain.specprofile.entity.AcademicAbility;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.enums.AcademicAbilityType;
import com.lifelog.domain.specprofile.enums.SpecProfileType;
import com.lifelog.domain.specprofile.repository.AcademicAbilityRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileRepository;
import com.lifelog.global.response.SliceResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    @Autowired private MemberRepository members;
    @Autowired private SpecProfileRepository profiles;
    @Autowired private AcademicAbilityRepository academics;
    private Member member;
    private SpecProfile current;

    @BeforeAll
    void prepareDatabase() throws Exception {
        MajorTestDatabaseSupport.initialize(dataSource);
    }

    @BeforeEach
    void 준비() {
        member = members.saveAndFlush(new Member("major-search@example.com"));
        current = profiles.saveAndFlush(SpecProfile.builder().member(member).type(SpecProfileType.CURRENT).build());
        saveAcademic(current, AcademicAbilityType.UNIVERSITY);
    }

    @AfterEach
    void cleanUp() {
        academics.deleteAllInBatch();
        profiles.deleteAllInBatch();
        members.deleteAllInBatch();
        repository.deleteAllInBatch();
    }

    @Test
    @DisplayName("앞뒤 공백을 제거하여 조회하고 한글 분류명과 학위과정을 매핑한다")
    void 검색어_정규화_응답매핑_성공() {
        // given
        Major saved = saveMajor("컴퓨터공학과");
        // when
        SliceResponse<MajorListResDto> response = service.searchMajors(member.getId(), "  컴퓨터공학  ", MajorMainField.ENGINEERING, 1, 20);
        // then
        assertThat(response.totalElements()).isEqualTo(1);
        MajorResDto major = response.content().majors().getFirst();
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
            assertThat(service.searchMajors(member.getId(), keyword, null, 1, 100).totalElements()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("검색어 길이는 앞뒤 공백 제거 후 100자까지 허용한다")
    void 검색어_길이_경계값_성공() {
        // given
        saveMajor("가".repeat(100));
        // when / then
        assertThat(service.searchMajors(member.getId(), " " + "가".repeat(100) + " ", null, 1, 1).totalElements()).isEqualTo(1);
        assertThatThrownBy(() -> service.searchMajors(member.getId(), "가".repeat(101), null, 1, 20))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MajorErrorCode.INVALID_KEYWORD));
    }

    @Test
    @DisplayName("페이지 번호와 크기가 허용 범위를 벗어나면 공통 오류를 반환한다")
    void 페이지_검증_실패_범위초과() {
        // given / when / then
        assertThatThrownBy(() -> service.searchMajors(member.getId(), null, null, 0, 20))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PAGE));
        for (int size : new int[]{0, 101}) {
            assertThatThrownBy(() -> service.searchMajors(member.getId(), null, null, 1, size))
                    .isInstanceOfSatisfying(CustomException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_SIZE));
        }
    }

    @Test
    @DisplayName("학사 회원의 조회에는 타인 CURRENT와 본인 TARGET의 전문학사 전공이 섞이지 않는다")
    void 회원CURRENT학력_필터_성공() {
        // given
        saveMajor("컴퓨터공학과");
        saveMajor("C01", "건물관리과", DegreeProcess.COLLEGE, MajorMainField.ENGINEERING);
        Member other = members.saveAndFlush(new Member("other-major@example.com"));
        saveAcademic(profiles.saveAndFlush(SpecProfile.builder().member(other).type(SpecProfileType.CURRENT).build()),
                AcademicAbilityType.COLLEGE);
        saveAcademic(profiles.saveAndFlush(SpecProfile.builder().member(member).type(SpecProfileType.TARGET).build()),
                AcademicAbilityType.COLLEGE);
        // when
        SliceResponse<MajorListResDto> result = service.searchMajors(member.getId(), null, null, 1, 20);
        // then
        assertThat(result.content().majors()).extracting(MajorResDto::code).containsExactly("U01");
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"HIGH_SCHOOL,HIGH_SCHOOL", "COLLEGE,COLLEGE", "UNIVERSITY,UNIVERSITY",
            "GRADUATE_SCHOOL_MASTER,GRADUATE_SCHOOL", "GRADUATE_SCHOOL_DOCTORAL,GRADUATE_SCHOOL",
            "GRADUATE_SCHOOL,GRADUATE_SCHOOL"})
    @DisplayName("CURRENT 학력 유형에 대응하는 학위과정의 전공만 반환한다")
    void 학력유형별_학위매핑_성공(AcademicAbilityType type, DegreeProcess expected) {
        // given
        academics.deleteAllInBatch();
        saveAcademic(current, type);
        for (DegreeProcess degree : DegreeProcess.values()) {
            saveMajor(degree.name(), "동일학과", degree, MajorMainField.ENGINEERING);
        }
        // when
        SliceResponse<MajorListResDto> result = service.searchMajors(member.getId(), null, null, 1, 20);
        // then
        assertThat(result.content().majors()).extracting(MajorResDto::degreeProcessName).containsExactly(expected);
    }

    @Test
    @DisplayName("복수 학력은 중복 없는 학위과정으로 합쳐 조회하고 같은 조건으로 전체 건수를 계산한다")
    void 복수학력_검색과페이징_성공() {
        // given
        saveAcademic(current, AcademicAbilityType.COLLEGE);
        saveAcademic(current, AcademicAbilityType.GRADUATE_SCHOOL_MASTER);
        saveAcademic(current, AcademicAbilityType.GRADUATE_SCHOOL_DOCTORAL);
        saveMajor("C01", "컴퓨터공학과", DegreeProcess.COLLEGE, MajorMainField.ENGINEERING);
        saveMajor("G01", "컴퓨터공학과", DegreeProcess.GRADUATE_SCHOOL, MajorMainField.ENGINEERING);
        saveMajor("컴퓨터공학과");
        saveMajor("H01", "컴퓨터공학과", DegreeProcess.HIGH_SCHOOL, MajorMainField.ENGINEERING);
        saveMajor("U02", "컴퓨터공학과", DegreeProcess.UNIVERSITY, MajorMainField.HUMANITIES);
        saveMajor("U03", "전자공학과", DegreeProcess.UNIVERSITY, MajorMainField.ENGINEERING);
        // when
        SliceResponse<MajorListResDto> result = service.searchMajors(member.getId(), "컴퓨터", MajorMainField.ENGINEERING, 2, 1);
        // then
        assertThat(result.content().majors()).extracting(MajorResDto::code).containsExactly("G01");
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    @DisplayName("CURRENT 학력이 없거나 ETC만 있거나 CURRENT 프로필이 없으면 빈 목록을 반환한다")
    void 조회가능학력없음_빈목록_성공() {
        // given
        saveMajor("컴퓨터공학과");
        academics.deleteAllInBatch();
        // when / then
        assertThat(service.searchMajors(member.getId(), null, null, 2, 20).totalElements()).isZero();
        saveAcademic(current, AcademicAbilityType.ETC);
        SliceResponse<MajorListResDto> result = service.searchMajors(member.getId(), null, null, 2, 20);
        assertThat(result.content().majors()).isEmpty();
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.hasNext()).isFalse();
        academics.deleteAllInBatch();
        profiles.deleteAllInBatch();
        assertThat(service.searchMajors(member.getId(), null, null, 1, 20).content().majors()).isEmpty();
    }

    private void saveAcademic(SpecProfile profile, AcademicAbilityType type) {
        academics.saveAndFlush(AcademicAbility.builder().specProfile(profile).type(type).build());
    }

    private Major saveMajor(String code, String name, DegreeProcess degree, MajorMainField field) {
        return repository.saveAndFlush(Major.builder().code(code).name(name).mainField(field)
                .middleField(MajorMiddleField.COMPUTER_COMMUNICATIONS)
                .subField(MajorSubField.COMPUTER_SCIENCE_ENGINEERING).degreeProcess(degree).build());
    }

    private Major saveMajor(String name) {
        return repository.saveAndFlush(Major.builder().code("U01").name(name).mainField(MajorMainField.ENGINEERING)
                .middleField(MajorMiddleField.COMPUTER_COMMUNICATIONS)
                .subField(MajorSubField.COMPUTER_SCIENCE_ENGINEERING).degreeProcess(DegreeProcess.UNIVERSITY).build());
    }
}
