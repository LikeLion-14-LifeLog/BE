package com.lifelog.domain.specprofile.mapper;

import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.entity.*;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.languageexam.entity.LanguageExam;
import org.springframework.stereotype.Component;

@Component
public class SpecProfileMapper {
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

