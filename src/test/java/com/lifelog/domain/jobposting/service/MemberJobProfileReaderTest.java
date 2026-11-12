package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.job.entity.DesiredJob;
import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.DesiredJobRepository;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.domain.specprofile.entity.AcademicAbility;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.enums.AcademicAbilityStatus;
import com.lifelog.domain.specprofile.enums.AcademicAbilityType;
import com.lifelog.domain.specprofile.enums.SpecProfileType;
import com.lifelog.domain.specprofile.repository.AcademicAbilityRepository;
import com.lifelog.domain.specprofile.repository.SpecProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MemberJobProfileReaderTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Autowired
    private MemberJobProfileReader reader;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private DesiredJobRepository desiredJobRepository;

    @Autowired
    private SpecProfileRepository specProfileRepository;

    @Autowired
    private AcademicAbilityRepository academicAbilityRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(new Member("member-job-profile@test.com"));
    }

    @Test
    void 희망_직무의_소분류와_CURRENT_스펙의_가장_높은_학력을_읽음() {
        saveDesiredJob(member, saveJob("1", "13", "133", "1332"));
        SpecProfile current = saveSpecProfile(member, SpecProfileType.CURRENT);
        saveAcademicAbility(current, AcademicAbilityType.HIGH_SCHOOL, AcademicAbilityStatus.GRADUATED);
        // 재학 중이어도 졸업예정이 12개월 이내면 그 단계로 인정
        saveAcademicAbility(current, AcademicAbilityType.UNIVERSITY, AcademicAbilityStatus.ATTENDING,
                TODAY.plusMonths(6));

        MemberJobProfile profile = reader.read(member.getId());

        assertThat(profile.subCategoryCodes()).containsExactly("133");
        assertThat(profile.educationLevel()).isEqualTo(5);
        assertThat(profile.isPersonalizable()).isTrue();
    }

    @Test
    void 다른_회원의_희망_직무는_읽지_않음() {
        Member other = memberRepository.save(new Member("member-job-profile-other@test.com"));
        saveDesiredJob(other, saveJob("1", "13", "133", "1332"));

        assertThat(reader.read(member.getId()).subCategoryCodes()).isEmpty();
    }

    @Test
    void 석사는_6_박사는_7() {
        SpecProfile current = saveSpecProfile(member, SpecProfileType.CURRENT);
        saveAcademicAbility(current, AcademicAbilityType.GRADUATE_SCHOOL_MASTER, AcademicAbilityStatus.GRADUATED);
        assertThat(reader.read(member.getId()).educationLevel()).isEqualTo(6);

        saveAcademicAbility(current, AcademicAbilityType.GRADUATE_SCHOOL_DOCTORAL, AcademicAbilityStatus.ATTENDING,
                TODAY.plusMonths(6));
        assertThat(reader.read(member.getId()).educationLevel()).isEqualTo(7);
    }

    @Test
    void 목표_스펙의_학력과_ETC는_학력_판단에_쓰지_않음() {
        SpecProfile current = saveSpecProfile(member, SpecProfileType.CURRENT);
        SpecProfile target = saveSpecProfile(member, SpecProfileType.TARGET);
        saveAcademicAbility(current, AcademicAbilityType.COLLEGE, AcademicAbilityStatus.LEAVE_FROM_SCHOOL,
                TODAY.plusMonths(6));
        saveAcademicAbility(current, AcademicAbilityType.ETC, AcademicAbilityStatus.GRADUATED);
        saveAcademicAbility(target, AcademicAbilityType.GRADUATE_SCHOOL_DOCTORAL, AcademicAbilityStatus.GRADUATED);

        assertThat(reader.read(member.getId()).educationLevel()).isEqualTo(4);
    }

    @Test
    void 졸업이면_인정() {
        SpecProfile current = saveSpecProfile(member, SpecProfileType.CURRENT);
        saveAcademicAbility(current, AcademicAbilityType.UNIVERSITY, AcademicAbilityStatus.GRADUATED,
                TODAY.minusYears(1));

        assertThat(reader.read(member.getId()).educationLevel()).isEqualTo(5);
    }

    @Test
    void 졸업유예면_졸업예정_연월과_관계없이_인정() {
        SpecProfile current = saveSpecProfile(member, SpecProfileType.CURRENT);
        saveAcademicAbility(current, AcademicAbilityType.UNIVERSITY, AcademicAbilityStatus.GRADUATE_POSTPONED,
                TODAY.plusYears(2));

        assertThat(reader.read(member.getId()).educationLevel()).isEqualTo(5);
    }

    @Test
    void 재학이고_졸업예정이_6개월_뒤면_인정() {
        SpecProfile current = saveSpecProfile(member, SpecProfileType.CURRENT);
        saveAcademicAbility(current, AcademicAbilityType.UNIVERSITY, AcademicAbilityStatus.ATTENDING,
                TODAY.plusMonths(6));

        assertThat(reader.read(member.getId()).educationLevel()).isEqualTo(5);
    }

    @Test
    void 재학이고_졸업예정이_2년_뒤면_미인정이라_고졸_단계() {
        SpecProfile current = saveSpecProfile(member, SpecProfileType.CURRENT);
        saveAcademicAbility(current, AcademicAbilityType.UNIVERSITY, AcademicAbilityStatus.ATTENDING,
                TODAY.plusYears(2));

        assertThat(reader.read(member.getId()).educationLevel()).isEqualTo(3);
    }

    @Test
    void 휴학이고_졸업예정이_11개월_뒤면_인정() {
        SpecProfile current = saveSpecProfile(member, SpecProfileType.CURRENT);
        saveAcademicAbility(current, AcademicAbilityType.COLLEGE, AcademicAbilityStatus.LEAVE_FROM_SCHOOL,
                TODAY.plusMonths(11));

        assertThat(reader.read(member.getId()).educationLevel()).isEqualTo(4);
    }

    @Test
    void 재학이고_졸업예정이_12개월_뒤_달까지는_인정_13개월_뒤면_미인정() {
        SpecProfile current = saveSpecProfile(member, SpecProfileType.CURRENT);
        saveAcademicAbility(current, AcademicAbilityType.COLLEGE, AcademicAbilityStatus.ATTENDING,
                TODAY.plusMonths(12));
        assertThat(reader.read(member.getId()).educationLevel()).isEqualTo(4);

        saveAcademicAbility(current, AcademicAbilityType.UNIVERSITY, AcademicAbilityStatus.ATTENDING,
                TODAY.plusMonths(13));
        assertThat(reader.read(member.getId()).educationLevel()).isEqualTo(4);
    }

    @Test
    void 재학이고_졸업예정이_이미_지났으면_인정() {
        SpecProfile current = saveSpecProfile(member, SpecProfileType.CURRENT);
        saveAcademicAbility(current, AcademicAbilityType.UNIVERSITY, AcademicAbilityStatus.ATTENDING,
                TODAY.minusMonths(3));

        assertThat(reader.read(member.getId()).educationLevel()).isEqualTo(5);
    }

    @Test
    void 재학이고_졸업예정_연월이_없으면_미인정() {
        SpecProfile current = saveSpecProfile(member, SpecProfileType.CURRENT);
        saveAcademicAbility(current, AcademicAbilityType.UNIVERSITY, AcademicAbilityStatus.ATTENDING);

        assertThat(reader.read(member.getId()).educationLevel()).isEqualTo(3);
    }

    @Test
    void 대학원_재학_2년_뒤_졸업과_대학교_졸업이면_대졸_단계() {
        SpecProfile current = saveSpecProfile(member, SpecProfileType.CURRENT);
        saveAcademicAbility(current, AcademicAbilityType.GRADUATE_SCHOOL_MASTER, AcademicAbilityStatus.ATTENDING,
                TODAY.plusYears(2));
        saveAcademicAbility(current, AcademicAbilityType.UNIVERSITY, AcademicAbilityStatus.GRADUATED,
                TODAY.minusYears(1));

        assertThat(reader.read(member.getId()).educationLevel()).isEqualTo(5);
    }

    @Test
    void 학력_미입력이면_학력_조건을_적용하지_않도록_null() {
        saveDesiredJob(member, saveJob("1", "13", "133", "1332"));
        saveSpecProfile(member, SpecProfileType.CURRENT);

        MemberJobProfile profile = reader.read(member.getId());

        assertThat(profile.isPersonalizable()).isTrue();
        assertThat(profile.educationLevel()).isNull();
    }

    @Test
    void 희망_직무가_미분류면_소분류가_없어_맞춤_불가() {
        saveDesiredJob(member, saveJob(null, null, null, "9999"));

        MemberJobProfile profile = reader.read(member.getId());

        assertThat(profile.subCategoryCodes()).isEmpty();
        assertThat(profile.isPersonalizable()).isFalse();
    }

    @Test
    void 희망_직무와_학력이_없으면_맞춤_불가이고_학력은_null() {
        MemberJobProfile profile = reader.read(member.getId());

        assertThat(profile.subCategoryCodes()).isEmpty();
        assertThat(profile.educationLevel()).isNull();
        assertThat(profile.isPersonalizable()).isFalse();
    }

    @Test
    void 비로그인이면_빈_프로필() {
        assertThat(reader.read(null)).isEqualTo(MemberJobProfile.empty());
    }

    private Job saveJob(String mainCode, String middleCode, String subCode, String code) {
        return jobRepository.saveAndFlush(Job.builder()
                .mainCategoryCode(mainCode).mainCategoryName(mainCode == null ? "미분류" : "대분류 " + mainCode)
                .middleCategoryCode(middleCode).middleCategoryName(middleCode == null ? null : "중분류 " + middleCode)
                .subCategoryCode(subCode).subCategoryName(subCode == null ? null : "소분류 " + subCode)
                .code(code).name("직무 " + code)
                .build());
    }

    private void saveDesiredJob(Member owner, Job job) {
        desiredJobRepository.saveAndFlush(DesiredJob.builder().member(owner).job(job).build());
    }

    private SpecProfile saveSpecProfile(Member owner, SpecProfileType type) {
        return specProfileRepository.saveAndFlush(SpecProfile.builder().member(owner).type(type).build());
    }

    private void saveAcademicAbility(SpecProfile profile, AcademicAbilityType type, AcademicAbilityStatus status) {
        saveAcademicAbility(profile, type, status, null);
    }

    private void saveAcademicAbility(SpecProfile profile, AcademicAbilityType type, AcademicAbilityStatus status,
                                     LocalDate graduatedAt) {
        academicAbilityRepository.saveAndFlush(AcademicAbility.builder()
                .specProfile(profile).type(type).status(status).graduatedAt(graduatedAt)
                .build());
    }
}
