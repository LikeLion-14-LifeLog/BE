package com.lifelog.domain.specprofile;

import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.certification.enums.*;
import com.lifelog.domain.certification.repository.CertificationRepository;
import com.lifelog.domain.languageexam.entity.*;
import com.lifelog.domain.languageexam.repository.LanguageExamRepository;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.major.enums.*;
import com.lifelog.domain.major.repository.MajorRepository;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;
import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.entity.*;
import com.lifelog.domain.specprofile.enums.*;
import com.lifelog.domain.specprofile.mapper.SpecProfileAnalyzationMapper;
import com.lifelog.domain.specprofile.repository.*;
import com.lifelog.domain.specprofile.service.SpecProfileQueryService;
import com.lifelog.domain.specprofile.service.specprofiletarget.SpecProfileTargetSaveService;
import com.lifelog.domain.specprofile.service.specprofiletarget.SpecProfileTargetQueryService;
import com.lifelog.domain.specprofile.service.specprofilesimulation.SpecProfileSimulationCacheRegistry;
import com.lifelog.global.infra.openai.OpenApiUtil;
import com.lifelog.global.security.JwtProvider;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:target-query;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpecProfileTargetQueryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired JwtProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @Autowired SpecProfileRepository profiles;
    @Autowired MajorRepository majors;
    @Autowired AcademicAbilityRepository academics;
    @Autowired AcademicAbilityMajorRepository academicMajors;
    @Autowired CertificationRepository certifications;
    @Autowired SpecProfileCertificateRepository certificates;
    @Autowired LanguageExamRepository exams;
    @Autowired LanguageExamScoreRepository scores;
    @Autowired ExperienceRepository experiences;
    @Autowired SpecProfileQueryService currentQuery;
    @Autowired SpecProfileTargetQueryService targetQuery;
    @Autowired SpecProfileTargetSaveService targetSave;
    @Autowired SpecProfileAnalyzationMapper snapshots;
    @Autowired SpecProfileSimulationCacheRegistry cache;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoBean OpenApiUtil openApiUtil;
    Member member;
    SpecProfile current;
    Major major;
    Certification certificate;

    @BeforeEach
    void 준비() {
        member = members.saveAndFlush(new Member(UUID.randomUUID() + "@target-query.test"));
        current = profiles.saveAndFlush(SpecProfile.builder().member(member).birthYear((short) 1999).build());
        major = majors.saveAndFlush(Major.builder().name("컴퓨터공학과").code("TQ001").mainField(MajorMainField.ENGINEERING)
                .middleField(MajorMiddleField.OTHER).subField(MajorSubField.NURSING).degreeProcess(DegreeProcess.UNIVERSITY).build());
        AcademicAbility owned = academics.saveAndFlush(AcademicAbility.builder().specProfile(current).type(AcademicAbilityType.UNIVERSITY)
                .status(AcademicAbilityStatus.GRADUATED).location(ResidentialArea.SEOUL).universityGrade(new BigDecimal("3.75"))
                .universityStandardGrade(new BigDecimal("4.5")).graduatedAt(LocalDate.of(2024, 2, 1)).build());
        academicMajors.saveAndFlush(AcademicAbilityMajor.builder().academicAbility(owned).major(major).build());
        certificate = certifications.saveAndFlush(Certification.builder().name("정보처리기사").code("TQ1")
                .type(CertificationType.PRIVATE).mainCategory(CertificationMainCategory.COMMON).build());
        exams.saveAndFlush(LanguageExam.builder().id(7L).name("OPIc").languageType(LanguageType.ENGLISH).scoreType(ScoreType.GRADE).build());
        experiences.saveAndFlush(Experience.builder().specProfile(current).type(ExperienceType.PROJECT)
                .title("현재 프로젝트").startedAt(LocalDate.of(2024, 1, 1)).build());
    }

    @AfterEach
    void 정리() {
        cache.evict(member.getId());
        for (String table : List.of("academic_ability_major", "academic_ability", "spec_profile_certificate",
                "language_exam_score", "experience", "spec_profile", "major", "certificate", "language_exam", "member")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @Test
    @DisplayName("추가 항목의 TARGET PK·이름·연월만 반환하며 AI·DB·캐시는 변경하지 않는다")
    void 추가목표조회_저장값과읽기전용_성공() throws Exception {
        // given
        Long targetId = save(fullChanges(), CompanyGroupScale.LARGE);
        SpecProfileSimulationContext cached = new SpecProfileSimulationContext(snapshots.toSnapshot(currentQuery.getCurrentProfile(member.getId())), null);
        cache.store(member.getId(), cached);
        Map<String, List<Map<String, Object>>> before = databaseState();
        // when
        JsonNode result = data(query().andExpect(status().isOk()).andExpect(jsonPath("$.message").value("요청 성공")));
        // then
        assertThat(result.path("targetSpecProfileId").asLong()).isEqualTo(targetId);
        assertThat(result.path("baseSpecProfileId").asLong()).isEqualTo(current.getId());
        assertThat(result.path("companyGroupScale").asString()).isEqualTo("LARGE");
        JsonNode changes = result.path("changes");
        assertThat(changes.path("academicAbilities").size()).isEqualTo(1);
        assertThat(changes.path("academicAbilities").get(0).path("majorName").asString()).isEqualTo("컴퓨터공학과");
        assertThat(changes.path("academicAbilities").get(0).path("universityGrade").decimalValue()).isEqualByComparingTo("4.0");
        assertThat(changes.path("academicAbilities").get(0).path("academicAbilityId").asLong())
                .isEqualTo(academics.findAllBySpecProfileId(targetId).stream().filter(AcademicAbility::isTargetAdditional).findFirst().orElseThrow().getId());
        assertThat(changes.path("certificates").get(0).path("certificateName").asString()).isEqualTo("정보처리기사");
        assertThat(changes.path("certificates").get(0).path("acquiredYearMonth").isNull()).isTrue();
        assertThat(changes.path("languageExamScores").get(0).path("languageExamName").asString()).isEqualTo("OPIc");
        assertThat(changes.path("languageExamScores").get(0).path("scoreNumeric").isNull()).isTrue();
        assertThat(changes.path("languageExamScores").get(0).path("scoreGrade").asString()).isEqualTo("IH");
        assertThat(changes.path("experiences").size()).isEqualTo(1);
        assertThat(changes.path("experiences").get(0).path("startedYearMonth").asString()).isEqualTo("2027-01");
        assertThat(changes.path("experiences").get(0).path("endedYearMonth").isNull()).isTrue();
        assertThat(result.has("salaryResults")).isFalse();
        assertThat(databaseState()).isEqualTo(before);
        assertThat(cache.find(member.getId()).orElseThrow()).isSameAs(cached);
        verifyNoInteractions(openApiUtil);
    }

    @ParameterizedTest
    @ValueSource(strings = {"없음", "false", "null"})
    @DisplayName("목표가 없거나 선택 여부가 false·null이면 명시적인 data:null을 반환한다")
    void 미선택목표_널응답_성공(String state) throws Exception {
        // given
        if (!state.equals("없음")) {
            Long id = save(fullChanges(), null);
            jdbc.update("UPDATE spec_profile SET target_spec_selected=" + state + " WHERE spec_profile_id=?", id);
        }
        // when / then
        String json = query().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json).contains("\"data\":null");
        verifyNoInteractions(openApiUtil);
    }

    @Test
    @DisplayName("추가 조건이 없는 정상 목표는 미지정 기업 규모와 네 빈 배열을 반환한다")
    void 추가조건없음_빈목록반환_성공() throws Exception {
        // given
        save(SpecProfileTargetChanges.empty(), null);
        // when
        JsonNode response = data(query().andExpect(status().isOk()));
        // then
        assertThat(response.has("companyGroupScale")).isTrue();
        assertThat(response.path("companyGroupScale").isNull()).isTrue();
        response.path("changes").properties().forEach(entry -> assertThat(entry.getValue().size()).isZero());
    }

    @Test
    @DisplayName("기존 목표는 409 재저장을 안내하고 다시 저장하면 조회할 수 있다")
    void 이전목표_재저장안내후복구_성공() throws Exception {
        // given
        Long id = save(fullChanges(), CompanyGroupScale.LARGE);
        jdbc.update("UPDATE spec_profile SET target_conditions_recorded=false WHERE spec_profile_id=?", id);
        Map<String, List<Map<String, Object>>> before = databaseState();
        // when / then
        query().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TARGET_409_3"));
        assertThat(databaseState()).isEqualTo(before);
        assertThat(save(fullChanges(), CompanyGroupScale.SMALL)).isEqualTo(id);
        query().andExpect(status().isOk()).andExpect(jsonPath("$.data.companyGroupScale").value("SMALL"));
    }

    @Test
    @DisplayName("CURRENT가 수정되거나 추가 목표와 같은 항목이 생겨도 저장된 추가 목표를 유지한다")
    void 현재스펙변경_저장된추가목표유지_성공() throws Exception {
        // given
        save(fullChanges(), null);
        JsonNode previous = data(query().andExpect(status().isOk()));
        certificates.saveAndFlush(SpecProfileCertificate.builder().specProfile(current).certificate(certificate).acquiredAt(LocalDate.of(2026, 1, 1)).build());
        jdbc.update("UPDATE experience SET title='수정한 현재 경험' WHERE spec_profile_id=?", current.getId());
        // when / then
        assertThat(data(query().andExpect(status().isOk()))).isEqualTo(previous);
    }

    @Test
    @DisplayName("값이 같은 복제 경험과 추가 경험도 출처로 구분하며 순서와 개수를 보존한다")
    void 동일내용경험_추가항목만반환_성공() throws Exception {
        // given
        SpecProfileTargetChanges.ExperienceChange same = new SpecProfileTargetChanges.ExperienceChange(ExperienceType.PROJECT,
                "현재 프로젝트", YearMonth.of(2024, 1), null, null, false, null, null);
        Long id = save(new SpecProfileTargetChanges(null, null, null, List.of(same, same)), null);
        // when
        JsonNode items = data(query().andExpect(status().isOk())).path("changes").path("experiences");
        // then
        assertThat(experiences.findAllBySpecProfileId(id)).hasSize(3);
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).path("experienceId").asLong()).isLessThan(items.get(1).path("experienceId").asLong());
    }

    @Test
    @DisplayName("타인의 선택 목표는 제외하고 본인 선택 목표를 반환한다")
    void 타인목표격리_성공() throws Exception {
        // given
        Long id = save(fullChanges(), null);
        Member other = members.saveAndFlush(new Member(UUID.randomUUID() + "@other.test"));
        SpecProfile otherCurrent = profiles.saveAndFlush(SpecProfile.builder().member(other).build());
        profiles.saveAndFlush(SpecProfile.builder().member(other).type(SpecProfileType.TARGET).baseSpecProfile(otherCurrent)
                .targetSpecSelected(true).build());
        // when / then
        query().andExpect(status().isOk()).andExpect(jsonPath("$.data.targetSpecProfileId").value(id));
    }

    @Test
    @DisplayName("선택 목표가 중복되면 거절하며 재저장 시 이전 선택을 해제한다")
    void 중복선택_오류후재저장복구_성공() throws Exception {
        // given
        save(fullChanges(), null);
        SpecProfile previous = profiles.saveAndFlush(SpecProfile.builder().member(member).type(SpecProfileType.TARGET)
                .sortOrder((short) 2).baseSpecProfile(current).targetSpecSelected(true).build());
        // when / then
        query().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TARGET_409_2"));
        save(fullChanges(), null);
        assertThat(profiles.findById(previous.getId()).orElseThrow().getTargetSpecSelected()).isFalse();
        query().andExpect(status().isOk());
    }

    @Test
    @DisplayName("기준 CURRENT 관계가 없으면 불완전 목표를 반환하지 않는다")
    void 기준관계누락_실패() throws Exception {
        // given
        Long id = save(fullChanges(), null);
        jdbc.update("UPDATE spec_profile SET base_spec_profile_id=null WHERE spec_profile_id=?", id);
        // when / then
        query().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TARGET_409_2"));
    }

    @Test
    @DisplayName("CURRENT가 없으면 생성하지 않고 404, 중복이면 무결성 오류를 반환한다")
    void 현재프로필개수_실패() throws Exception {
        // given
        profiles.saveAndFlush(SpecProfile.builder().member(member).build());
        // when / then
        query().andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("SPEC_PROFILE_500_1"));
        jdbc.update("DELETE FROM academic_ability_major");
        jdbc.update("DELETE FROM academic_ability");
        jdbc.update("DELETE FROM experience");
        jdbc.update("DELETE FROM spec_profile");
        query().andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SPEC_PROFILE_404_1"));
        assertThat(profiles.count()).isZero();
    }

    @Test
    @DisplayName("미수상 항목의 잘못 남은 수상 정보는 응답에서 null로 반환한다")
    void 미수상상세_널보정_성공() throws Exception {
        // given
        Long id = save(fullChanges(), null);
        jdbc.update("UPDATE experience SET organizer_name='이전 값', award_grade='대상' WHERE spec_profile_id=?", id);
        // when
        JsonNode item = data(query().andExpect(status().isOk())).path("changes").path("experiences").get(0);
        // then
        assertThat(item.path("awarded").asBoolean()).isFalse();
        assertThat(item.path("organizerName").isNull()).isTrue();
        assertThat(item.path("awardGrade").isNull()).isTrue();
    }

    @Test
    @DisplayName("추가 학력의 전공 미입력은 null이고 복수 연결은 무결성 오류다")
    void 전공연결_널또는무결성오류_성공() throws Exception {
        // given
        Long id = save(fullChanges(), null);
        AcademicAbility additional = academics.findAllBySpecProfileId(id).stream().filter(AcademicAbility::isTargetAdditional).findFirst().orElseThrow();
        jdbc.update("DELETE FROM academic_ability_major WHERE academic_ability_id=?", additional.getId());
        // when / then
        query().andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("\"majorName\":null")));
        academicMajors.saveAndFlush(AcademicAbilityMajor.builder().academicAbility(additional).major(major).build());
        Major other = majors.saveAndFlush(Major.builder().name("다른 전공").code("TQ002").mainField(major.getMainField())
                .middleField(major.getMiddleField()).subField(major.getSubField()).degreeProcess(major.getDegreeProcess()).build());
        academicMajors.saveAndFlush(AcademicAbilityMajor.builder().academicAbility(additional).major(other).build());
        query().andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("SPEC_PROFILE_500_3"));
    }

    @Test
    @DisplayName("항목 수가 늘어도 이름 조회 쿼리가 항목별로 증가하지 않는다")
    void 조회쿼리수_일괄조회_성공() {
        // given
        Long id = save(fullChanges(), null);
        SessionFactory factory = entityManagerFactory.unwrap(SessionFactory.class);
        factory.getStatistics().clear();
        targetQuery.getSelectedTarget(member.getId());
        long small = factory.getStatistics().getPrepareStatementCount();
        for (int index = 0; index < 10; index++) {
            AcademicAbility academic = academics.saveAndFlush(AcademicAbility.builder().specProfile(profiles.findById(id).orElseThrow())
                    .type(AcademicAbilityType.UNIVERSITY).status(AcademicAbilityStatus.ATTENDING).location(ResidentialArea.SEOUL).targetAdditional(true).build());
            academicMajors.saveAndFlush(AcademicAbilityMajor.builder().academicAbility(academic).major(major).build());
        }
        factory.getStatistics().clear();
        // when
        SpecProfileTargetResDto result = targetQuery.getSelectedTarget(member.getId());
        // then
        assertThat(result.changes().academicAbilities()).hasSize(11);
        assertThat(factory.getStatistics().getPrepareStatementCount()).isEqualTo(small);
    }

    @Test
    @DisplayName("기존 목표 덮어쓰기 실패 시 기업 규모·추가 항목 구분과 선택 상태도 롤백한다")
    void 목표덮어쓰기실패_조회조건전체롤백_성공() throws Exception {
        // given
        save(fullChanges(), CompanyGroupScale.LARGE);
        JsonNode previous = data(query().andExpect(status().isOk()));
        jdbc.execute("ALTER TABLE experience ADD CONSTRAINT target_query_reject CHECK (title <> '실패 경험')");
        try {
            SpecProfileTargetChanges invalid = new SpecProfileTargetChanges(null, null, null,
                    List.of(new SpecProfileTargetChanges.ExperienceChange(ExperienceType.PROJECT, "실패 경험", YearMonth.of(2027, 1), null, null, false, null, null)));
            // when / then
            assertThatThrownBy(() -> save(invalid, CompanyGroupScale.SMALL)).isInstanceOf(RuntimeException.class);
            assertThat(data(query().andExpect(status().isOk()))).isEqualTo(previous);
        } finally {
            jdbc.execute("ALTER TABLE experience DROP CONSTRAINT target_query_reject");
        }
    }

    @Test
    @DisplayName("토큰이 없거나 잘못되면 인증 오류를 반환한다")
    void 인증실패_실패() throws Exception {
        // given / when / then
        mvc.perform(get("/api/spec-profile/target")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/spec-profile/target").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    }

    protected Long save(SpecProfileTargetChanges changes, CompanyGroupScale scale) {
        return targetSave.upsertUserTarget(member.getId(), snapshots.toSnapshot(currentQuery.getCurrentProfile(member.getId())), changes, scale);
    }

    protected SpecProfileTargetChanges fullChanges() {
        return new SpecProfileTargetChanges(List.of(new SpecProfileTargetChanges.AcademicAbilityChange(AcademicAbilityType.GRADUATE_SCHOOL_MASTER,
                AcademicAbilityStatus.ATTENDING, ResidentialArea.SEOUL, new BigDecimal("4.0"), new BigDecimal("4.5"), null, major.getMainField(), major.getId())),
                List.of(new SpecProfileTargetChanges.CertificateChange(certificate.getId(), null)),
                List.of(new SpecProfileTargetChanges.LanguageExamScoreChange(7L, null, "IH", null, null)),
                List.of(new SpecProfileTargetChanges.ExperienceChange(ExperienceType.INTERNSHIP, "백엔드 개발 인턴", YearMonth.of(2027, 1), null,
                        "API 개발 및 서비스 운영 경험", false, null, null)));
    }

    protected ResultActions query() throws Exception {
        return mvc.perform(get("/api/spec-profile/target").header("Authorization", "Bearer " + jwt.createAccessToken(member.getId())));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }

    private Map<String, List<Map<String, Object>>> databaseState() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : List.of("spec_profile", "academic_ability", "academic_ability_major", "spec_profile_certificate", "language_exam_score",
                "experience", "spec_profile_analyzation_run", "spec_profile_analyzation_result", "spec_profile_analyzation_event_log")) {
            result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
        }
        return result;
    }
}
