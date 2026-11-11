package com.lifelog.domain.specprofile;

import com.lifelog.domain.auth.dto.SignupRequest;
import com.lifelog.domain.auth.service.AuthService;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.*;
import com.lifelog.domain.job.entity.Job;
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
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.domain.specprofile.repository.*;
import com.lifelog.domain.specprofile.service.SpecProfileService;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.security.JwtProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:spec-save;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpecProfileSaveIntegrationTest {
    @Autowired
    SpecProfileService service;
    @Autowired AuthService authService;
    @Autowired MemberRepository members;
    @Autowired MemberIdentityRepository identities;
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
    Member member;
    SpecProfile profile;
    Job job;
    Major major;
    Certification certificate;
    LanguageExam exam;

    @BeforeEach
    void prepare() {
        clean();
        member = members.saveAndFlush(new Member("spec@example.com"));
        profile = service.createCurrentProfile(member);
        job = jobs.saveAndFlush(Job.builder().code("0001").name("개발").mainCategoryName("개발").build());
        major = majors.saveAndFlush(Major.builder().name("전공").code("TEST")
                .mainField(MajorMainField.ENGINEERING).middleField(MajorMiddleField.OTHER)
                .subField(MajorSubField.NURSING).degreeProcess(DegreeProcess.UNIVERSITY).build());
        certificate = certifications.saveAndFlush(Certification.builder().name("자격증")
                .type(CertificationType.PRIVATE).mainCategory(CertificationMainCategory.COMMON).build());
        exam = exams.saveAndFlush(LanguageExam.builder().id(1L).name("숫자시험")
                .languageType(LanguageType.ENGLISH).scoreType(ScoreType.NUMERIC).build());
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
        identities.deleteAllInBatch();
        members.deleteAllInBatch();
        certifications.deleteAllInBatch();
        majors.deleteAllInBatch();
        jobs.deleteAllInBatch();
        exams.deleteAllInBatch();
    }

    @Test
    @DisplayName("네 목록은 PK를 유지하며 입력값으로 덮어쓴다")
    void 전체목록_PK유지수정_성공() {
        // given
        service.saveTemporary(member.getId(), fullRequest(null, null, null, null, "처음", "2024-01"));
        Long academicId = academics.findAll().getFirst().getId();
        Long certificateId = certificates.findAll().getFirst().getId();
        Long scoreId = scores.findAll().getFirst().getId();
        Long experienceId = experiences.findAll().getFirst().getId();
        Long linkId = academicMajors.findAll().getFirst().getId();
        // when
        service.saveTemporary(member.getId(), fullRequest(academicId, certificateId, scoreId, experienceId, "수정", "2024-02"));
        // then
        assertThat(academics.findAll()).singleElement().satisfies(a -> {
            assertThat(a.getId()).isEqualTo(academicId);
            assertThat(a.getGraduatedAt()).isEqualTo(YearMonth.of(2024, 2).atDay(1));
        });
        assertThat(academicMajors.findAll()).singleElement().extracting(AcademicAbilityMajor::getId).isEqualTo(linkId);
        assertThat(certificates.findAll()).singleElement().satisfies(c -> {
            assertThat(c.getId()).isEqualTo(certificateId);
            assertThat(c.getAcquiredAt()).isEqualTo(YearMonth.of(2024, 2).atDay(1));
        });
        assertThat(scores.findAll()).singleElement().satisfies(s -> {
            assertThat(s.getId()).isEqualTo(scoreId);
            assertThat(s.getAcquiredAt()).isEqualTo(YearMonth.of(2024, 2).atDay(1));
        });
        assertThat(experiences.findAll()).singleElement().satisfies(e -> {
            assertThat(e.getId()).isEqualTo(experienceId);
            assertThat(e.getTitle()).isEqualTo("수정");
        });
        assertThat(desiredJobs.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("기존 자격증 및 어학 성적의 참조 FK도 PK를 유지하며 덮어쓴다")
    void 참조FK_덮어쓰기_성공() {
        // given
        service.saveTemporary(member.getId(), fullRequest(null, null, null, null, "기존", "2024-01"));
        Long certificateId = certificates.findAll().getFirst().getId();
        Long scoreId = scores.findAll().getFirst().getId();
        Certification nextCertificate = certifications.saveAndFlush(Certification.builder().name("새 자격증")
                .type(CertificationType.PRIVATE).mainCategory(CertificationMainCategory.COMMON).build());
        LanguageExam nextExam = exams.saveAndFlush(LanguageExam.builder().id(2L).name("등급시험")
                .languageType(LanguageType.ENGLISH).scoreType(ScoreType.GRADE).build());
        SpecProfileTemporarySaveReqDto request = new SpecProfileTemporarySaveReqDto(1999, ResidentialArea.SEOUL, job.getId(), null,
                List.of(new CertificateSaveReqDto(certificateId, nextCertificate.getId(), YearMonth.of(2024, 2))),
                List.of(new LanguageExamScoreSaveReqDto(scoreId, nextExam.getId(), null, "임의등급",
                        YearMonth.of(2024, 2), null)), List.of());
        // when
        service.saveTemporary(member.getId(), request);
        // then
        assertThat(certificates.findAll()).singleElement().satisfies(c -> {
            assertThat(c.getId()).isEqualTo(certificateId);
            assertThat(c.getCertificate().getId()).isEqualTo(nextCertificate.getId());
        });
        assertThat(scores.findAll()).singleElement().satisfies(s -> {
            assertThat(s.getId()).isEqualTo(scoreId);
            assertThat(s.getLanguageExam().getId()).isEqualTo(nextExam.getId());
            assertThat(s.getScoreNumeric()).isNull();
            assertThat(s.getScoreGrade()).isEqualTo("임의등급");
        });
    }

    @Test
    @DisplayName("학력 null과 빈 선택 목록은 기존 항목을 삭제하고 TARGET은 유지한다")
    void 학력null_기존목록삭제_성공() {
        // given
        service.saveTemporary(member.getId(), fullRequest(null, null, null, null, "처음", "2024-01"));
        SpecProfile target = profiles.saveAndFlush(SpecProfile.builder().member(member).type(SpecProfileType.TARGET).sortOrder((short) 1).build());
        Experience targetExperience = experiences.saveAndFlush(Experience.builder().specProfile(target)
                .type(ExperienceType.PROJECT).title("TARGET").build());
        // when
        service.saveTemporary(member.getId(), new SpecProfileTemporarySaveReqDto(1999, ResidentialArea.DAEGU,
                job.getId(), null, List.of(), List.of(), List.of()));
        // then
        assertThat(academics.count()).isZero();
        assertThat(academicMajors.count()).isZero();
        assertThat(certificates.count()).isZero();
        assertThat(scores.count()).isZero();
        assertThat(experiences.findAll()).singleElement().extracting(Experience::getId).isEqualTo(targetExperience.getId());
    }

    @Test
    @DisplayName("PK 없이 재전송한 항목은 기존 행 삭제 후 신규 생성된다")
    void PK없는항목_대체생성_성공() {
        // given
        service.saveTemporary(member.getId(), fullRequest(null, null, null, null, "처음", "2024-01"));
        Long oldCertificate = certificates.findAll().getFirst().getId();
        Long oldScore = scores.findAll().getFirst().getId();
        // when
        service.saveTemporary(member.getId(), fullRequest(null, null, null, null, "새 항목", "2024-02"));
        // then
        assertThat(certificates.findAll()).singleElement().satisfies(c -> assertThat(c.getId()).isNotEqualTo(oldCertificate));
        assertThat(scores.findAll()).singleElement().satisfies(s -> assertThat(s.getId()).isNotEqualTo(oldScore));
        assertThat(academics.count()).isEqualTo(1);
        assertThat(academicMajors.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("후반 경험 소유권 검증 실패 시 앞선 삭제와 수정도 롤백된다")
    void 후반실패_전체롤백_성공() {
        // given
        service.saveTemporary(member.getId(), fullRequest(null, null, null, null, "기존", "2024-01"));
        Long oldAcademic = academics.findAll().getFirst().getId();
        Long oldCertificate = certificates.findAll().getFirst().getId();
        Member other = members.saveAndFlush(new Member("other@example.com"));
        SpecProfile otherProfile = service.createCurrentProfile(other);
        Long otherExperience = experiences.saveAndFlush(Experience.builder().specProfile(otherProfile)
                .type(ExperienceType.PROJECT).title("타인").build()).getId();
        // when / then
        assertThatThrownBy(() -> service.saveTemporary(member.getId(),
                fullRequest(null, null, null, otherExperience, "실패", "2024-02")))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SpecProfileErrorCode.CHILD_NOT_FOUND));
        assertThat(academics.findAll()).singleElement().extracting(AcademicAbility::getId).isEqualTo(oldAcademic);
        assertThat(certificates.findAll()).singleElement().extracting(SpecProfileCertificate::getId).isEqualTo(oldCertificate);
        assertThat(experiences.findAllBySpecProfileId(profile.getId())).singleElement()
                .extracting(Experience::getTitle).isEqualTo("기존");
    }

    @Test
    @DisplayName("어학 마스터가 없으면 초기화 필요 예외를 반환하고 저장하지 않는다")
    void 저장_실패_어학데이터미초기화() {
        // given
        exams.deleteAllInBatch();
        // when / then
        assertThatThrownBy(() -> service.saveTemporary(member.getId(), emptyRequest(1999, "미저장")))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo("어학 데이터 초기화가 필요합니다");
                    assertThat(e.getErrorCode()).isEqualTo(SpecProfileErrorCode.LANGUAGE_EXAM_NOT_INITIALIZED);
                });
        assertThat(profiles.findById(profile.getId()).orElseThrow().getBirthYear()).isNull();
        assertThat(desiredJobs.count()).isZero();
    }

    @Test
    @DisplayName("동시 CURRENT 생성은 회원 잠금으로 하나만 유지한다")
    void CURRENT_동시생성_성공() throws Exception {
        // given
        profiles.deleteAllInBatch();
        // when
        runTogether(() -> service.createCurrentProfile(member), () -> service.createCurrentProfile(member));
        // then
        assertThat(profiles.countByMemberIdAndType(member.getId(), SpecProfileType.CURRENT)).isEqualTo(1);
    }

    @Test
    @DisplayName("동시 저장 후 기본 정보와 하위 목록은 하나의 요청 상태로 일치한다")
    void 동시저장_상태일관성_성공() throws Exception {
        // given / when
        runTogether(() -> service.saveTemporary(member.getId(), emptyRequest(1998, "A")),
                () -> service.saveTemporary(member.getId(), emptyRequest(1999, "B")));
        // then
        short birthYear = profiles.findById(profile.getId()).orElseThrow().getBirthYear();
        String title = experiences.findAllBySpecProfileId(profile.getId()).getFirst().getTitle();
        assertThat(title).isEqualTo(birthYear == 1998 ? "A" : "B");
        assertThat(experiences.count()).isEqualTo(1);
        assertThat(desiredJobs.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("가입 시 CURRENT는 기본값으로 생성된다")
    void 회원가입_CURRENT생성_성공() {
        // given / when
        Long id = authService.signup(new SignupRequest("signup@example.com", "password123"));
        // then
        List<SpecProfile> created = profiles.findAllByMemberIdAndType(id, SpecProfileType.CURRENT);
        assertThat(created).singleElement().satisfies(p -> {
            assertThat(p.getSortOrder()).isZero();
            assertThat(p.getBirthYear()).isNull();
        });
    }

    @Test
    @DisplayName("JWT로 학력 null을 저장하면 201과 data null을 반환한다")
    void API_학력null_성공() throws Exception {
        // given
        String token = jwt.createAccessToken(member.getId());
        // when / then
        mvc.perform(put("/api/spec-profiles/current/temporary").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(jsonBody("null", "[]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_201"))
                .andExpect(jsonPath("$.message").value("회원 스펙 임시 저장 성공"))
                .andExpect(content().json("""
                    {"success":true,"code":"COMMON_201","message":"회원 스펙 임시 저장 성공","data":null}
                    """));
    }

    @Test
    @DisplayName("미인증과 잘못된 JWT는 401, 잘못된 배열 요소는 400이다")
    void API_실패_인증및바인딩() throws Exception {
        // given / when / then
        mvc.perform(put("/api/spec-profiles/current/temporary").contentType("application/json")
                .content(jsonBody("null", "[]"))).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/spec-profiles/current/temporary").header("Authorization", "Bearer invalid")
                .contentType("application/json").content(jsonBody("null", "[]"))).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/spec-profiles/current/temporary")
                .header("Authorization", "Bearer " + jwt.createAccessToken(member.getId()))
                .contentType("application/json").content(jsonBody("[null]", "[]")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/spec-profiles/current/temporary")
                .header("Authorization", "Bearer " + jwt.createAccessToken(member.getId()))
                .contentType("application/json").content(jsonBody("null", "null")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("전체 JSON의 연월을 바인딩하고 잘못된 연월은 400으로 반환한다")
    void API_연월바인딩_검증() throws Exception {
        // given
        String body = """
            {"birthYear":1999,"residenceRegion":"DAEGU","jobId":%d,"academicAbilities":null,
             "certificates":[{"certificateId":%d,"acquiredYearMonth":"2024-11"}],
             "languageExamScores":[{"languageExamId":%d,"scoreNumeric":865,"scoreGrade":null,
               "acquiredYearMonth":"2024-08","expiresYearMonth":"2026-08"}],
             "experiences":[{"type":"PROJECT","title":"프로젝트","startedYearMonth":"2024-01",
               "endedYearMonth":null,"description":null,"awarded":false,"organizerName":null,"awardGrade":null}]}
            """.formatted(job.getId(), certificate.getId(), exam.getId());
        String token = jwt.createAccessToken(member.getId());
        // when / then
        mvc.perform(put("/api/spec-profiles/current/temporary").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body)).andExpect(status().isCreated());
        assertThat(scores.findAll()).singleElement().satisfies(score ->
                assertThat(score.getExpiresAt()).isEqualTo(YearMonth.of(2026, 8).atDay(1)));
        mvc.perform(put("/api/spec-profiles/current/temporary").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body.replace("2024-11", "2024-13")))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    @DisplayName("각 도메인은 타인 프로필의 PK를 거절하고 기존 상태를 유지한다")
    void 도메인별저장_실패_타인PK(int domainIndex) {
        // given
        service.saveTemporary(member.getId(), fullRequest(null, null, null, null, "본인", "2024-01"));
        Member otherMember = members.saveAndFlush(new Member("foreign@example.com"));
        SpecProfile otherProfile = service.createCurrentProfile(otherMember);
        service.saveTemporary(otherMember.getId(), fullRequest(null, null, null, null, "타인", "2024-01"));
        Long academicId = academics.findAllBySpecProfileId(profile.getId()).getFirst().getId();
        Long certificateId = certificates.findAllBySpecProfileId(profile.getId()).getFirst().getId();
        Long scoreId = scores.findAllBySpecProfileId(profile.getId()).getFirst().getId();
        Long experienceId = experiences.findAllBySpecProfileId(profile.getId()).getFirst().getId();
        SpecProfileTemporarySaveReqDto request = fullRequest(
                domainIndex == 0 ? academics.findAllBySpecProfileId(otherProfile.getId()).getFirst().getId() : academicId,
                domainIndex == 1 ? certificates.findAllBySpecProfileId(otherProfile.getId()).getFirst().getId() : certificateId,
                domainIndex == 2 ? scores.findAllBySpecProfileId(otherProfile.getId()).getFirst().getId() : scoreId,
                domainIndex == 3 ? experiences.findAllBySpecProfileId(otherProfile.getId()).getFirst().getId() : experienceId,
                "수정 시도", "2024-02");
        // when / then
        assertThatThrownBy(() -> service.saveTemporary(member.getId(), request))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(SpecProfileErrorCode.CHILD_NOT_FOUND));
        assertThat(academics.findById(academicId).orElseThrow().getGraduatedAt())
                .isEqualTo(YearMonth.of(2024, 1).atDay(1));
        assertThat(certificates.findById(certificateId).orElseThrow().getAcquiredAt())
                .isEqualTo(YearMonth.of(2024, 1).atDay(1));
        assertThat(scores.findById(scoreId).orElseThrow().getAcquiredAt())
                .isEqualTo(YearMonth.of(2024, 1).atDay(1));
        assertThat(experiences.findById(experienceId).orElseThrow().getTitle()).isEqualTo("본인");
        assertThat(experiences.findAllBySpecProfileId(otherProfile.getId())).singleElement()
                .extracting(Experience::getTitle).isEqualTo("타인");
    }

    @Test
    @DisplayName("전공과 희망 직무를 변경해도 학력 연결과 희망 직무의 PK를 유지한다")
    void 전공및희망직무_참조변경_성공() {
        // given
        service.saveTemporary(member.getId(), fullRequest(null, null, null, null, "기존", "2024-01"));
        Long academicId = academics.findAll().getFirst().getId();
        Long linkId = academicMajors.findAll().getFirst().getId();
        Long desiredJobId = desiredJobs.findByMemberId(member.getId()).orElseThrow().getId();
        Major nextMajor = majors.saveAndFlush(Major.builder().name("변경 전공").code("NEXT")
                .mainField(MajorMainField.ENGINEERING).middleField(MajorMiddleField.OTHER)
                .subField(MajorSubField.NURSING).degreeProcess(DegreeProcess.UNIVERSITY).build());
        Job nextJob = jobs.saveAndFlush(Job.builder().code("0002").name("설계").mainCategoryName("개발").build());
        AcademicAbilitySaveReqDto academicRequest = new AcademicAbilitySaveReqDto(academicId,
                AcademicAbilityType.UNIVERSITY, AcademicAbilityStatus.GRADUATED, ResidentialArea.SEOUL,
                new BigDecimal("3.75"), new BigDecimal("4.5"), YearMonth.of(2024, 2),
                MajorMainField.ENGINEERING, nextMajor.getId());
        // when
        service.saveTemporary(member.getId(), new SpecProfileTemporarySaveReqDto(1998, ResidentialArea.SEOUL,
                nextJob.getId(), List.of(academicRequest), List.of(), List.of(), List.of()));
        // then
        assertThat(academicMajors.findAll()).singleElement().satisfies(link -> {
            assertThat(link.getId()).isEqualTo(linkId);
            assertThat(link.getAcademicAbility().getId()).isEqualTo(academicId);
            assertThat(link.getMajor().getId()).isEqualTo(nextMajor.getId());
        });
        assertThat(desiredJobs.findByMemberId(member.getId()).orElseThrow()).satisfies(desiredJob -> {
            assertThat(desiredJob.getId()).isEqualTo(desiredJobId);
            assertThat(desiredJob.getJob().getId()).isEqualTo(nextJob.getId());
        });
        assertThat(profiles.findById(profile.getId()).orElseThrow()).satisfies(savedProfile -> {
            assertThat(savedProfile.getBirthYear()).isEqualTo((short) 1998);
            assertThat(savedProfile.getResidenceRegion()).isEqualTo(ResidentialArea.SEOUL);
        });
    }

    @Test
    @DisplayName("종료월과 수상 정보를 초기화하고 어학 성적의 만료월을 null로 덮어쓴다")
    void 경험및어학성적_nullable필드초기화_성공() {
        // given
        YearMonth month = YearMonth.of(2024, 1);
        service.saveTemporary(member.getId(), new SpecProfileTemporarySaveReqDto(1999, ResidentialArea.DAEGU,
                job.getId(), null, List.of(),
                List.of(new LanguageExamScoreSaveReqDto(null, exam.getId(), new BigDecimal("865"), null,
                        month, YearMonth.of(2026, 1))),
                List.of(new ExperienceSaveReqDto(null, ExperienceType.CONTEST, "수상 활동", month,
                        YearMonth.of(2024, 2), "성과", true, "주관 기관", "대상"))));
        Long scoreId = scores.findAll().getFirst().getId();
        Long experienceId = experiences.findAll().getFirst().getId();
        // when
        service.saveTemporary(member.getId(), new SpecProfileTemporarySaveReqDto(1999, ResidentialArea.DAEGU,
                job.getId(), null, List.of(),
                List.of(new LanguageExamScoreSaveReqDto(scoreId, exam.getId(), new BigDecimal("900"), null, month, null)),
                List.of(new ExperienceSaveReqDto(experienceId, ExperienceType.PROJECT, "진행 중", month,
                        null, null, false, null, null))));
        // then
        assertThat(scores.findAll()).singleElement().satisfies(score -> {
            assertThat(score.getId()).isEqualTo(scoreId);
            assertThat(score.getScoreNumeric()).isEqualByComparingTo("900");
            assertThat(score.getExpiresAt()).isNull();
        });
        assertThat(experiences.findAll()).singleElement().satisfies(experience -> {
            assertThat(experience.getId()).isEqualTo(experienceId);
            assertThat(experience.getType()).isEqualTo(ExperienceType.PROJECT);
            assertThat(experience.getEndedAt()).isNull();
            assertThat(experience.getDescription()).isNull();
            assertThat(experience.isAwarded()).isFalse();
            assertThat(experience.getOrganizerName()).isNull();
            assertThat(experience.getAwardGrade()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"academicAbilities", "certificates", "languageExamScores", "experiences"})
    @DisplayName("모든 목록에서 null 요소는 400으로 거절하고 저장하지 않는다")
    void API_실패_목록의null요소(String field) throws Exception {
        // given
        String body = jsonBody("[]", "[]").replace("\"" + field + "\":[]", "\"" + field + "\":[null]");
        // when / then
        mvc.perform(put("/api/spec-profiles/current/temporary")
                .header("Authorization", "Bearer " + jwt.createAccessToken(member.getId()))
                .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        assertThat(profiles.findById(profile.getId()).orElseThrow().getBirthYear()).isNull();
        assertThat(desiredJobs.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"certificates", "languageExamScores", "experiences"})
    @DisplayName("학력 외 목록 자체의 null은 400으로 거절한다")
    void API_실패_필수목록null(String field) throws Exception {
        // given
        String body = jsonBody("null", "[]").replace("\"" + field + "\":[]", "\"" + field + "\":null");
        // when / then
        mvc.perform(put("/api/spec-profiles/current/temporary")
                .header("Authorization", "Bearer " + jwt.createAccessToken(member.getId()))
                .contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    private String jsonBody(String academic, String certificates) {
        return """
            {"birthYear":1999,"residenceRegion":"DAEGU","jobId":%d,
             "academicAbilities":%s,"certificates":%s,"languageExamScores":[],"experiences":[]}
            """.formatted(job.getId(), academic, certificates);
    }

    private SpecProfileTemporarySaveReqDto fullRequest(Long academicId, Long certificateId, Long scoreId,
                                                        Long experienceId, String title, String date) {
        YearMonth month = YearMonth.parse(date);
        return new SpecProfileTemporarySaveReqDto(1999, ResidentialArea.DAEGU, job.getId(),
                List.of(new AcademicAbilitySaveReqDto(academicId, AcademicAbilityType.UNIVERSITY,
                        AcademicAbilityStatus.GRADUATED, ResidentialArea.SEOUL, new BigDecimal("3.75"),
                        new BigDecimal("4.5"), month, MajorMainField.ENGINEERING, major.getId())),
                List.of(new CertificateSaveReqDto(certificateId, certificate.getId(), month)),
                List.of(new LanguageExamScoreSaveReqDto(scoreId, exam.getId(), new BigDecimal("865"),
                        null, month, null)),
                List.of(experience(experienceId, title)));
    }

    private SpecProfileTemporarySaveReqDto emptyRequest(int birthYear, String title) {
        return new SpecProfileTemporarySaveReqDto(birthYear, ResidentialArea.DAEGU, job.getId(),
                null, List.of(), List.of(), List.of(experience(null, title)));
    }

    private ExperienceSaveReqDto experience(Long id, String title) {
        return new ExperienceSaveReqDto(id, ExperienceType.PROJECT, title, YearMonth.of(2024, 1),
                null, null, false, null, null);
    }

    private void runTogether(Runnable first, Runnable second) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = List.of(first, second).stream().map(action -> executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("시작 대기 시간 초과");
                action.run();
                return null;
            })).toList();
            start.countDown();
            for (Future<Object> future : futures) future.get(30, TimeUnit.SECONDS);
        }
    }
}

