package com.lifelog.domain.specprofile;

import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.domain.job.entity.*;
import com.lifelog.domain.job.repository.*;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.major.enums.*;
import com.lifelog.domain.major.repository.MajorRepository;
import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.certification.enums.*;
import com.lifelog.domain.certification.repository.CertificationRepository;
import com.lifelog.domain.languageexam.entity.*;
import com.lifelog.domain.languageexam.repository.LanguageExamRepository;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.entity.*;
import com.lifelog.domain.specprofile.enums.*;
import com.lifelog.domain.specprofile.repository.*;
import com.lifelog.domain.specprofile.service.SpecProfileService;
import com.lifelog.global.security.JwtProvider;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:spec-query;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpecProfileQueryIntegrationTest {
    @Autowired SpecProfileService service;
    @Autowired MemberRepository members;
    @Autowired SpecProfileRepository profiles;
    @Autowired DesiredJobRepository desiredJobs;
    @Autowired JobRepository jobs;
    @Autowired MajorRepository majors;
    @Autowired CertificationRepository certifications;
    @Autowired LanguageExamRepository exams;
    @Autowired AcademicAbilityRepository academics;
    @Autowired AcademicAbilityMajorRepository academicMajors;
    @Autowired SpecProfileCertificateRepository certificates;
    @Autowired LanguageExamScoreRepository scores;
    @Autowired ExperienceRepository experiences;
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Value("${jwt.secret}") String jwtSecret;
    Member member;
    SpecProfile profile;
    Job job;
    DesiredJob desiredJob;

    @BeforeEach
    void prepare() {
        member = members.saveAndFlush(new Member(UUID.randomUUID() + "@query.test"));
        profile = service.createCurrentProfile(member);
        job = jobs.saveAndFlush(Job.builder().code("9000").name("기본 직무").mainCategoryName("개발").build());
        desiredJob = desiredJobs.saveAndFlush(DesiredJob.builder().member(member).job(job).build());
    }

    @AfterEach
    void clean() {
        academicMajors.deleteAllInBatch();
        academics.deleteAllInBatch();
        certificates.deleteAllInBatch();
        scores.deleteAllInBatch();
        experiences.deleteAllInBatch();
        desiredJobs.deleteAllInBatch();
        profiles.deleteAllInBatch();
        members.deleteAllInBatch();
        certifications.deleteAllInBatch();
        majors.deleteAllInBatch();
        jobs.deleteAllInBatch();
        exams.deleteAllInBatch();
    }

    @Test
    @DisplayName("미입력 CURRENT는 어학 초기화 없이 null과 빈 배열을 반환하며 DB를 수정하지 않는다")
    void 초기프로필조회_성공() throws Exception {
        // given
        desiredJobs.delete(desiredJob);
        LocalDateTime updatedAt = profiles.findById(profile.getId()).orElseThrow().getUpdatedAt();
        // when / then
        query().andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.message").value("회원 스펙 조회 성공"))
                .andExpect(jsonPath("$.data.specProfileId").value(profile.getId()))
                .andExpect(jsonPath("$.data.type").value("CURRENT"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"birthYear\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"desiredJobName\":null")))
                .andExpect(jsonPath("$.data.academicAbilities").isEmpty())
                .andExpect(jsonPath("$.data.certificates").isEmpty())
                .andExpect(jsonPath("$.data.languageExamScores").isEmpty())
                .andExpect(jsonPath("$.data.experiences").isEmpty())
                .andExpect(jsonPath("$.data.createdAt", org.hamcrest.Matchers.endsWith("+09:00")));
        assertThat(exams.count()).isZero();
        var result = service.getCurrentProfile(member.getId());
        assertThat(result.createdAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(result.createdAt().toInstant()).isEqualTo(
                profiles.findById(profile.getId()).orElseThrow().getCreatedAt()
                        .atZone(ZoneId.systemDefault()).toInstant());
        assertThat(profiles.count()).isEqualTo(1);
        assertThat(profiles.findById(profile.getId()).orElseThrow().getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("전체 저장 결과의 이름, 별도 DesiredJob PK, 연월, 소수 및 수상 정보를 반환한다")
    void 저장결과조회_성공() throws Exception {
        // given
        Job job = jobs.saveAndFlush(Job.builder().code("1000").name("백엔드 개발자").mainCategoryName("개발").build());
        jobs.saveAndFlush(Job.builder().code("1001").name("다른 직무").mainCategoryName("개발").build());
        Member other = members.saveAndFlush(new Member("other@query.test"));
        desiredJobs.saveAndFlush(DesiredJob.builder().member(other).job(job).build());
        desiredJobs.delete(desiredJob);
        DesiredJob desired = desiredJobs.saveAndFlush(DesiredJob.builder().member(member).job(job).build());
        // DB 시퀀스 상태와 무관하게 서로 다른 PK인 fixture를 보장한다.
        if (desired.getId().equals(job.getId())) {
            desiredJobs.delete(desired);
            desired = desiredJobs.saveAndFlush(DesiredJob.builder().member(member).job(job).build());
        }
        Major major = saveMajor("공학");
        Certification certificate = certifications.saveAndFlush(Certification.builder().name("정보처리기사")
                .type(CertificationType.PRIVATE).mainCategory(CertificationMainCategory.COMMON).build());
        LanguageExam exam = exams.saveAndFlush(LanguageExam.builder().id(1L).name("TOEIC")
                .languageType(LanguageType.ENGLISH).scoreType(ScoreType.NUMERIC).build());
        YearMonth month = YearMonth.of(2024, 8);
        var request = new SpecProfileTemporarySaveReqDto(1999, ResidentialArea.DAEGU, job.getId(),
                List.of(new AcademicAbilitySaveReqDto(null, AcademicAbilityType.UNIVERSITY,
                        AcademicAbilityStatus.GRADUATED, ResidentialArea.SEOUL, new BigDecimal("3.75"),
                        new BigDecimal("4.5"), month, MajorMainField.ENGINEERING, major.getId())),
                List.of(new CertificateSaveReqDto(null, certificate.getId(), month)),
                List.of(new LanguageExamScoreSaveReqDto(null, exam.getId(), new BigDecimal("865.5"), null,
                        month, YearMonth.of(2026, 8))),
                List.of(new ExperienceSaveReqDto(null, ExperienceType.CONTEST, "공모전", month, month,
                        "개발", true, "주관 기관", "대상")));
        service.saveTemporary(member.getId(), request);
        // when / then
        query().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birthYear").value(1999))
                .andExpect(jsonPath("$.data.residenceRegion").value("DAEGU"))
                .andExpect(jsonPath("$.data.desiredJobId").value(desired.getId()))
                .andExpect(jsonPath("$.data.jobId").value(job.getId()))
                .andExpect(jsonPath("$.data.desiredJobName").value("백엔드 개발자"))
                .andExpect(jsonPath("$.data.academicAbilities[0].majorId").value(major.getId()))
                .andExpect(jsonPath("$.data.academicAbilities[0].majorName").value("공학"))
                .andExpect(jsonPath("$.data.academicAbilities[0].majorAffiliation").value("ENGINEERING"))
                .andExpect(jsonPath("$.data.academicAbilities[0].universityGrade").value(3.75))
                .andExpect(jsonPath("$.data.academicAbilities[0].universityStandardGrade").value(4.5))
                .andExpect(jsonPath("$.data.academicAbilities[0].graduatedYearMonth").value("2024-08"))
                .andExpect(jsonPath("$.data.certificates[0].certificateName").value("정보처리기사"))
                .andExpect(jsonPath("$.data.certificates[0].acquiredYearMonth").value("2024-08"))
                .andExpect(jsonPath("$.data.languageExamScores[0].languageExamName").value("TOEIC"))
                .andExpect(jsonPath("$.data.languageExamScores[0].scoreNumeric").value(865.5))
                .andExpect(jsonPath("$.data.languageExamScores[0].expiresYearMonth").value("2026-08"))
                .andExpect(jsonPath("$.data.experiences[0].title").value("공모전"))
                .andExpect(jsonPath("$.data.experiences[0].startedYearMonth").value("2024-08"))
                .andExpect(jsonPath("$.data.experiences[0].awarded").value(true))
                .andExpect(jsonPath("$.data.experiences[0].awardGrade").value("대상"));
        assertThat(desired.getId()).isNotEqualTo(job.getId());
    }

    @Test
    @DisplayName("본인 TARGET과 타인 CURRENT의 항목은 조회되지 않는다")
    void 소유자와유형필터_성공() throws Exception {
        // given
        Member other = members.saveAndFlush(new Member("other@query.test"));
        SpecProfile otherProfile = service.createCurrentProfile(other);
        SpecProfile target = profiles.saveAndFlush(SpecProfile.builder().member(member).type(SpecProfileType.TARGET).build());
        experiences.saveAllAndFlush(List.of(
                Experience.builder().specProfile(otherProfile).type(ExperienceType.PROJECT).title("타인").build(),
                Experience.builder().specProfile(target).type(ExperienceType.PROJECT).title("TARGET").build()));
        // when / then
        query().andExpect(status().isOk()).andExpect(jsonPath("$.data.experiences").isEmpty());
    }

    @Test
    @DisplayName("CURRENT가 없으면 조회로 생성하지 않고 404를 반환한다")
    void 조회_실패_CURRENT없음() throws Exception {
        // given
        profiles.deleteAllInBatch();
        // when / then
        query().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SPEC_PROFILE_404_1"));
        assertThat(profiles.count()).isZero();
    }

    @Test
    @DisplayName("CURRENT 중복은 무결성 오류로 반환한다")
    void 조회_실패_CURRENT중복() throws Exception {
        // given
        profiles.saveAndFlush(SpecProfile.builder().member(member).build());
        // when / then
        query().andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("SPEC_PROFILE_500_1"));
    }

    @Test
    @DisplayName("전공 없는 부분 학력은 null을 유지하고 목록은 PK 순서로 반환한다")
    void 부분학력과목록정렬_성공() throws Exception {
        // given
        AcademicAbility first = savePartialAcademic();
        AcademicAbility second = savePartialAcademic();
        // when / then
        query().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.academicAbilities[0].academicAbilityId").value(first.getId()))
                .andExpect(jsonPath("$.data.academicAbilities[1].academicAbilityId").value(second.getId()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"majorName\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"graduatedYearMonth\":null")));
    }

    @Test
    @DisplayName("복수 전공을 임의로 선택하지 않고 무결성 오류로 반환한다")
    void 조회_실패_복수전공() throws Exception {
        // given
        AcademicAbility academic = savePartialAcademic();
        academicMajors.saveAllAndFlush(List.of(
                AcademicAbilityMajor.builder().academicAbility(academic).major(saveMajor("전공1")).build(),
                AcademicAbilityMajor.builder().academicAbility(academic).major(saveMajor("전공2")).build()));
        // when / then
        query().andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("SPEC_PROFILE_500_3"));
    }

    @Test
    @DisplayName("토큰 누락과 잘못된 토큰 및 만료 토큰은 401이다")
    void 인증_실패() throws Exception {
        // given
        String expired = new JwtProvider(jwtSecret, -60_000).createAccessToken(member.getId());
        // when / then
        mvc.perform(get("/api/spec-profile")).andExpect(status().isUnauthorized());
        for (String token : List.of("invalid", expired)) {
            mvc.perform(get("/api/spec-profile").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_401"));
        }
    }

    @Test
    @DisplayName("기본 정보가 같고 하위 항목만 바뀐 저장도 수정 시각과 최신 내용을 갱신한다")
    void 하위항목저장_수정시각갱신_성공() {
        // given
        Job job = jobs.saveAndFlush(Job.builder().code("1000").name("개발").mainCategoryName("개발").build());
        exams.saveAndFlush(LanguageExam.builder().id(1L).name("TOEIC")
                .languageType(LanguageType.ENGLISH).scoreType(ScoreType.NUMERIC).build());
        service.saveTemporary(member.getId(), saveExperienceRequest(job.getId(), "이전"));
        var old = service.getCurrentProfile(member.getId());
        jdbc.update("update spec_profile set updated_at = ? where spec_profile_id = ?",
                LocalDateTime.of(2020, 1, 1, 0, 0), profile.getId());
        // when
        service.saveTemporary(member.getId(), saveExperienceRequest(job.getId(), "최신"));
        var result = service.getCurrentProfile(member.getId());
        // then
        assertThat(result.createdAt()).isEqualTo(old.createdAt());
        assertThat(result.updatedAt().getYear()).isGreaterThan(2020);
        assertThat(result.experiences()).singleElement().extracting(ExperienceResDto::title).isEqualTo("최신");
    }

    @Test
    @DisplayName("학력 및 참조 항목 수가 늘어도 조회 쿼리 수는 일정하다")
    void 일괄참조조회_성공() {
        // given
        Major major = saveMajor("공학");
        for (int i = 0; i < 10; i++) {
            AcademicAbility academic = savePartialAcademic();
            academicMajors.saveAndFlush(AcademicAbilityMajor.builder().academicAbility(academic).major(major).build());
        }
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        // when
        var result = service.getCurrentProfile(member.getId());
        // then
        assertThat(result.academicAbilities()).hasSize(10).allSatisfy(a -> assertThat(a.majorName()).isEqualTo("공학"));
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(7);
    }

    @Test
    @DisplayName("어학 시험 점수 유형과 진행 중 경험의 null 값을 그대로 반환한다")
    void 등급점수와진행중경험_성공() throws Exception {
        // given
        LanguageExam grade = exams.saveAndFlush(LanguageExam.builder().id(1L).name("OPIc")
                .languageType(LanguageType.ENGLISH).scoreType(ScoreType.GRADE).build());
        LanguageExam both = exams.saveAndFlush(LanguageExam.builder().id(2L).name("복합시험")
                .languageType(LanguageType.ENGLISH).scoreType(ScoreType.NUMERIC_AND_GRADE).build());
        scores.saveAllAndFlush(List.of(
                LanguageExamScore.builder().specProfile(profile).languageExam(grade).scoreGrade("IH")
                        .acquiredAt(LocalDate.of(2024, 8, 1)).build(),
                LanguageExamScore.builder().specProfile(profile).languageExam(both).scoreGrade("A")
                        .scoreNumeric(new BigDecimal("100.5")).acquiredAt(LocalDate.of(2024, 8, 1)).build()));
        experiences.saveAndFlush(Experience.builder().specProfile(profile).type(ExperienceType.PROJECT)
                .title("진행 중").startedAt(LocalDate.of(2024, 1, 1)).build());
        // when / then
        query().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.languageExamScores[0].scoreGrade").value("IH"))
                .andExpect(jsonPath("$.data.languageExamScores[1].scoreNumeric").value(100.5))
                .andExpect(jsonPath("$.data.languageExamScores[1].scoreGrade").value("A"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"scoreNumeric\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"expiresYearMonth\":null")))
                .andExpect(jsonPath("$.data.experiences[0].awarded").value(false))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"endedYearMonth\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"organizerName\":null")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"awardGrade\":null")));
    }

    protected ResultActions query() throws Exception {
        return mvc.perform(get("/api/spec-profile").header("Authorization", "Bearer " + jwt.createAccessToken(member.getId())));
    }

    private Major saveMajor(String name) {
        return majors.saveAndFlush(Major.builder().name(name).code(UUID.randomUUID().toString().substring(0, 10))
                .mainField(MajorMainField.ENGINEERING).middleField(MajorMiddleField.OTHER)
                .subField(MajorSubField.NURSING).degreeProcess(DegreeProcess.UNIVERSITY).build());
    }

    private AcademicAbility savePartialAcademic() {
        return academics.saveAndFlush(AcademicAbility.builder().specProfile(profile)
                .type(AcademicAbilityType.HIGH_SCHOOL).status(AcademicAbilityStatus.GRADUATED)
                .location(ResidentialArea.SEOUL).build());
    }

    protected SpecProfileTemporarySaveReqDto saveExperienceRequest(Long jobId, String title) {
        return new SpecProfileTemporarySaveReqDto(1999, ResidentialArea.DAEGU, jobId, List.of(), List.of(),
                List.of(), List.of(new ExperienceSaveReqDto(null, ExperienceType.PROJECT, title,
                YearMonth.of(2024, 1), null, null, false, null, null)));
    }
}
