package com.lifelog.domain.specprofile.validation;

import com.lifelog.domain.languageexam.entity.LanguageExam;
import com.lifelog.domain.languageexam.repository.LanguageExamRepository;
import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SpecProfileAnalyzationInputValidator {
    private final LanguageExamRepository languageExamRepository;

    /**
     * 저장된 분석 입력의 필수값과 날짜 및 조건부 입력을 검증한다.
     * @param profile 잠금 상태에서 조회한 전체 스펙
     */
    public void validate(SpecProfileResDto profile) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        YearMonth currentMonth = YearMonth.from(today);
        require(profile.birthYear() != null && profile.birthYear() >= 1 && profile.birthYear() <= today.getYear());
        require(profile.residenceRegion() != null && profile.jobId() != null);
        require(!profile.academicAbilities().isEmpty());

        for (AcademicAbilityResDto academic : profile.academicAbilities()) {
            require(academic.type() != null && academic.status() != null && academic.location() != null);
            require(academic.graduatedYearMonth() != null && academic.majorId() != null && academic.majorAffiliation() != null);
            require((academic.universityGrade() == null) == (academic.universityStandardGrade() == null));
            if (academic.universityGrade() != null) {
                require(List.of("4.0", "4.3", "4.5", "100").stream()
                        .anyMatch(value -> new BigDecimal(value).compareTo(academic.universityStandardGrade()) == 0));
                require(academic.universityGrade().compareTo(academic.universityStandardGrade()) <= 0);
            }
        }
        for (CertificateResDto certificate : profile.certificates()) {
            require(certificate.certificateId() != null && certificate.acquiredYearMonth() != null);
        }

        Map<Long, LanguageExam> exams = languageExamRepository.findAllById(profile.languageExamScores().stream()
                .map(LanguageExamScoreResDto::languageExamId).toList()).stream()
                .collect(Collectors.toMap(LanguageExam::getId, Function.identity()));
        for (LanguageExamScoreResDto score : profile.languageExamScores()) {
            require(score.acquiredYearMonth() != null && !score.acquiredYearMonth().isAfter(currentMonth));
            require(score.expiresYearMonth() == null || !score.expiresYearMonth().isBefore(score.acquiredYearMonth()));
            LanguageExam exam = exams.get(score.languageExamId());
            require(exam != null);
            boolean numeric = score.scoreNumeric() != null;
            boolean grade = score.scoreGrade() != null && !score.scoreGrade().isBlank();
            require(switch (exam.getScoreType()) {
                case NUMERIC -> numeric && score.scoreGrade() == null;
                case GRADE -> !numeric && grade;
                case NUMERIC_AND_GRADE -> numeric && grade;
                case NONE -> !numeric && score.scoreGrade() == null;
            });
        }
        for (ExperienceResDto experience : profile.experiences()) {
            require(experience.type() != null && experience.title() != null && !experience.title().isBlank()
                    && experience.title().length() <= 255);
            require(experience.startedYearMonth() != null && !experience.startedYearMonth().isAfter(currentMonth));
            require(experience.endedYearMonth() == null || !experience.endedYearMonth().isBefore(experience.startedYearMonth()));
            require(experience.description() == null || experience.description().length() <= 300);
            if (experience.awarded()) {
                require(experience.organizerName() != null && !experience.organizerName().isBlank()
                        && experience.organizerName().length() <= 200);
                require(experience.awardGrade() != null && !experience.awardGrade().isBlank()
                        && experience.awardGrade().length() <= 50);
            } else {
                require(experience.organizerName() == null && experience.awardGrade() == null);
            }
        }
    }

    /** 입력 조건 위반을 분석 입력 오류로 변환한다. @param valid 입력 조건 충족 여부 */
    private void require(boolean valid) {
        if (!valid) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.INVALID_REQUEST);
        }
    }
}
