package com.lifelog.domain.specprofile.mapper;

import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.entity.*;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.languageexam.entity.LanguageExam;
import org.springframework.stereotype.Component;
import com.lifelog.domain.job.entity.DesiredJob;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Component
public class SpecProfileMapper {
    /**
     * 저장된 프로필과 하위 항목을 조회 응답으로 변환한다.
     * @param profile 본인 CURRENT 프로필
     * @param desiredJob 회원의 희망 직무 또는 null
     * @param academics 학력 목록
     * @param majors 학력 ID별 전공
     * @param certificates 보유 자격증 목록
     * @param scores 어학 성적 목록
     * @param experiences 활동 경험 목록
     * @return 미입력 값과 빈 목록을 유지한 응답
     */
    public SpecProfileResDto toSpecProfileResDto(SpecProfile profile, DesiredJob desiredJob,
            List<AcademicAbility> academics, Map<Long, Major> majors,
            List<SpecProfileCertificate> certificates, List<LanguageExamScore> scores,
            List<Experience> experiences) {
        return SpecProfileResDto.builder()
                .specProfileId(profile.getId()).type(profile.getType())
                .birthYear(profile.getBirthYear() == null ? null : profile.getBirthYear().intValue())
                .residenceRegion(profile.getResidenceRegion())
                .desiredJobId(desiredJob == null ? null : desiredJob.getId())
                .desiredJobName(desiredJob == null ? null : desiredJob.getJob().getName())
                .academicAbilities(academics.stream()
                        .map(a -> toAcademicAbilityResDto(a, majors.get(a.getId()))).toList())
                .certificates(certificates.stream().map(this::toCertificateResDto).toList())
                .languageExamScores(scores.stream().map(this::toLanguageExamScoreResDto).toList())
                .experiences(experiences.stream().map(this::toExperienceResDto).toList())
                .createdAt(toKoreanTime(profile.getCreatedAt()))
                .updatedAt(toKoreanTime(profile.getUpdatedAt())).build();
    }

    /** 학력과 선택 전공을 응답으로 변환한다. @param academic 학력 @param major 전공 또는 null @return 학력 응답 */
    public AcademicAbilityResDto toAcademicAbilityResDto(AcademicAbility academic, Major major) {
        return AcademicAbilityResDto.builder().academicAbilityId(academic.getId())
                .type(academic.getType()).status(academic.getStatus()).location(academic.getLocation())
                .universityGrade(academic.getUniversityGrade())
                .universityStandardGrade(academic.getUniversityStandardGrade())
                .graduatedYearMonth(toYearMonth(academic.getGraduatedAt()))
                .majorAffiliation(major == null ? null : major.getMainField())
                .majorId(major == null ? null : major.getId())
                .majorName(major == null ? null : major.getName()).build();
    }

    /** 보유 자격증을 이름과 함께 변환한다. @param certificate 보유 자격증 @return 자격증 응답 */
    public CertificateResDto toCertificateResDto(SpecProfileCertificate certificate) {
        return CertificateResDto.builder().specProfileCertificateId(certificate.getId())
                .certificateId(certificate.getCertificate().getId())
                .certificateName(certificate.getCertificate().getName())
                .acquiredYearMonth(toYearMonth(certificate.getAcquiredAt())).build();
    }

    /** 어학 성적을 시험명과 함께 변환한다. @param score 어학 성적 @return 어학 성적 응답 */
    public LanguageExamScoreResDto toLanguageExamScoreResDto(LanguageExamScore score) {
        return LanguageExamScoreResDto.builder().languageExamScoreId(score.getId())
                .languageExamId(score.getLanguageExam().getId())
                .languageExamName(score.getLanguageExam().getName())
                .scoreNumeric(score.getScoreNumeric()).scoreGrade(score.getScoreGrade())
                .acquiredYearMonth(toYearMonth(score.getAcquiredAt()))
                .expiresYearMonth(toYearMonth(score.getExpiresAt())).build();
    }

    /** 활동 경험의 저장 상태를 그대로 변환한다. @param experience 경험 @return 경험 응답 */
    public ExperienceResDto toExperienceResDto(Experience experience) {
        return ExperienceResDto.builder().experienceId(experience.getId()).type(experience.getType())
                .title(experience.getTitle()).startedYearMonth(toYearMonth(experience.getStartedAt()))
                .endedYearMonth(toYearMonth(experience.getEndedAt())).description(experience.getDescription())
                .awarded(experience.isAwarded()).organizerName(experience.getOrganizerName())
                .awardGrade(experience.getAwardGrade()).build();
    }

    /** 저장된 날짜를 연월로 변환한다. @param date 날짜 또는 null @return 연월 또는 null */
    private YearMonth toYearMonth(LocalDate date) {
        return date == null ? null : YearMonth.from(date);
    }

    /**
     * JVM 기본 시간대로 기록된 감사 시각을 한국 시간 오프셋으로 표시한다.
     * @param dateTime 감사 시각 또는 null
     * @return 같은 순간의 한국 시각 또는 null
     */
    private OffsetDateTime toKoreanTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
    }

    /** 회원의 기본 CURRENT 프로필을 생성한다. */
    public SpecProfile toCurrentProfile(Member member) {
        return SpecProfile.builder().member(member).build();
    }

    /** 학력 요청을 소유 프로필에 연결된 엔티티로 변환한다. */
    public AcademicAbility toAcademicAbility(AcademicAbilitySaveReqDto request, SpecProfile profile) {
        return AcademicAbility.builder().specProfile(profile).type(request.type()).status(request.status())
                .location(request.location()).universityGrade(request.universityGrade())
                .universityStandardGrade(request.universityStandardGrade())
                .graduatedAt(request.graduatedYearMonth().atDay(1)).build();
    }

    /** 학력과 전공을 연결한다. */
    public AcademicAbilityMajor toAcademicAbilityMajor(AcademicAbility academic, Major major) {
        return AcademicAbilityMajor.builder().academicAbility(academic).major(major).build();
    }

    /** 보유 자격증 요청을 엔티티로 변환한다. */
    public SpecProfileCertificate toCertificate(CertificateSaveReqDto request, SpecProfile profile, Certification certificate) {
        return SpecProfileCertificate.builder().specProfile(profile).certificate(certificate)
                .acquiredAt(request.acquiredYearMonth().atDay(1)).build();
    }

    /** 어학 성적 요청을 엔티티로 변환한다. */
    public LanguageExamScore toLanguageScore(LanguageExamScoreSaveReqDto request, SpecProfile profile, LanguageExam exam) {
        return LanguageExamScore.builder().specProfile(profile).languageExam(exam).scoreNumeric(request.scoreNumeric())
                .scoreGrade(request.scoreGrade()).acquiredAt(request.acquiredYearMonth().atDay(1))
                .expiresAt(request.expiresYearMonth() == null ? null : request.expiresYearMonth().atDay(1)).build();
    }

    /** 경험 요청을 엔티티로 변환한다. */
    public Experience toExperience(ExperienceSaveReqDto request, SpecProfile profile) {
        return Experience.builder().specProfile(profile).type(request.type()).title(request.title())
                .startedAt(request.startedYearMonth().atDay(1))
                .endedAt(request.endedYearMonth() == null ? null : request.endedYearMonth().atDay(1))
                .description(request.description()).awarded(request.awarded())
                .organizerName(request.organizerName()).awardGrade(request.awardGrade()).build();
    }
}

