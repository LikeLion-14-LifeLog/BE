package com.lifelog.domain.specprofile.validation;

import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.languageexam.entity.LanguageExam;
import com.lifelog.domain.languageexam.service.LanguageExamScoreValidator;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.specprofile.dto.SpecProfileResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationSnapshot;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationReqDto;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.domain.specprofile.service.SpecProfileSalaryScoreBandService;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SpecProfileSimulationInputValidator {
    private final LanguageExamScoreValidator languageScoreValidator;
    private final SpecProfileSalaryScoreBandService scoreBandService;

    /**
     * 추가할 목표 조건의 참조·중복·점수·수상 정보를 검증한다. 종료·취득 연월은 입력에서 제외한다.
     * @param base 회원의 현재 스펙
     * @param changes 일회성으로 추가할 조건
     * @param majors 참조 전공
     * @param certificates 참조 자격증
     * @param exams 참조 어학 시험
     */
    public void validate(SpecProfileResDto base, SpecProfileSimulationReqDto.Changes changes,
                         Map<Long, Major> majors, Map<Long, Certification> certificates, Map<Long, LanguageExam> exams) {
        Set<Long> certificateIds = new HashSet<>();
        base.certificates().forEach(value -> certificateIds.add(value.certificateId()));
        Set<Long> examIds = new HashSet<>();
        base.languageExamScores().forEach(value -> examIds.add(value.languageExamId()));
        validateChanges(changes, majors, certificates, exams, certificateIds, examIds);
    }

    /** 동일한 요청 시점 CURRENT 스냅샷을 사용해 AI 대안의 추가 조건을 검증한다. */
    public void validate(SpecProfileAnalyzationSnapshot base, SpecProfileSimulationReqDto.Changes changes,
                         Map<Long, Major> majors, Map<Long, Certification> certificates, Map<Long, LanguageExam> exams) {
        Set<Long> certificateIds = new HashSet<>();
        base.certificates().forEach(value -> certificateIds.add(value.certificateId()));
        Set<Long> examIds = new HashSet<>();
        base.languageExamScores().forEach(value -> examIds.add(value.languageExamId()));
        validateChanges(changes, majors, certificates, exams, certificateIds, examIds);
    }

    /** 현재 프로필과 독립 스냅샷에 동일한 참조·점수·중복 규칙을 적용한다. */
    private void validateChanges(SpecProfileSimulationReqDto.Changes changes,
            Map<Long, Major> majors, Map<Long, Certification> certificates, Map<Long, LanguageExam> exams,
            Set<Long> certificateIds, Set<Long> examIds) {
        for (SpecProfileSimulationReqDto.AcademicAbilityChange academic : changes.academicAbilities()) {
            Major major = majors.get(academic.majorId());
            requireReference(major != null);
            if (major.getMainField() != academic.majorAffiliation()) {
                throw new CustomException(SpecProfileErrorCode.INVALID_MAJOR_AFFILIATION_INPUT);
            }
            scoreBandService.gradeBand(academic.universityGrade(), academic.universityStandardGrade());
        }
        for (SpecProfileSimulationReqDto.CertificateChange certificate : changes.certificates()) {
            requireReference(certificates.containsKey(certificate.certificateId()));
            requireUnique(certificateIds.add(certificate.certificateId()));
        }
        for (SpecProfileSimulationReqDto.LanguageExamScoreChange score : changes.languageExamScores()) {
            LanguageExam exam = exams.get(score.languageExamId());
            requireReference(exam != null);
            requireUnique(examIds.add(score.languageExamId()));
            languageScoreValidator.validate(exam.getScoreType(), score.scoreNumeric(), score.scoreGrade());
        }
        for (SpecProfileSimulationReqDto.ExperienceChange experience : changes.experiences()) {
            if (Boolean.TRUE.equals(experience.awarded())) {
                require(experience.organizerName() != null && !experience.organizerName().isBlank());
                require(experience.awardGrade() != null && !experience.awardGrade().isBlank());
            } else {
                require(experience.organizerName() == null && experience.awardGrade() == null);
            }
        }
    }

    /** 참조 ID의 존재 여부를 검증한다. */
    private void requireReference(boolean valid) {
        if (!valid) throw new CustomException(SpecProfileErrorCode.REFERENCE_NOT_FOUND);
    }

    /** 현재 스펙과 목표 목록에 같은 자격증·시험이 중복되지 않는지 검증한다. */
    private void requireUnique(boolean valid) {
        if (!valid) throw new CustomException(SpecProfileErrorCode.DUPLICATE_ID);
    }

    /** 조건부 입력 위반을 기존 스펙 오류로 변환한다. */
    private void require(boolean valid) {
        if (!valid) throw new CustomException(SpecProfileErrorCode.INVALID_INPUT);
    }
}
