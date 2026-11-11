package com.lifelog.domain.specprofile.validation;

import com.lifelog.domain.specprofile.dto.*;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.languageexam.repository.LanguageExamRepository;
import com.lifelog.domain.languageexam.entity.LanguageExam;
import com.lifelog.domain.languageexam.service.LanguageExamScoreValidator;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.global.exception.CustomException;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
@Slf4j
public class SpecProfileSaveRequestValidator {
    private final LanguageExamRepository languageExamRepository;
    private final LanguageExamScoreValidator scoreValidator;

    /**
     * 요청값을 실제 조회된 전공, 자격증, 어학 시험 정보와 비교하여 검증한다.
     * @param request 저장할 전체 스펙
     * @param majors 실제 조회된 전공 ID별 정보
     * @param certifications 실제 조회된 자격증 ID별 정보
     * @param exams 실제 조회된 어학 시험 ID별 정보
     */
    public void validateRequestAgainstFetchedReferenceData(
            SpecProfileTemporarySaveReqDto request, Map<Long, Major> majors,
            Map<Long, Certification> certifications, Map<Long, LanguageExam> exams) {
        log.debug("[SpecProfileSaveRequestValidator] validateRequestAgainstFetchedReferenceData() - START");
        for (var academic : request.academicAbilities()) {
            if (requireFetchedReference(majors, academic.majorId()).getMainField() != academic.majorAffiliation()) {
                log.error("[SpecProfileSaveRequestValidator] 입력한 major의 전공계열과 실제 major의 전공계열이 맞지 않습니다 | validateRequestAgainstFetchedReferenceData() - END");
                throw new CustomException(SpecProfileErrorCode.INVALID_MAJOR_AFFILIATION_INPUT);
            }
        }
        for (var certificate : request.certificates()) {
            requireFetchedReference(certifications, certificate.certificateId());
        }
        for (var score : request.languageExamScores()) {
            scoreValidator.validate(score, requireFetchedReference(exams, score.languageExamId()).getScoreType());
        }
        log.debug("[SpecProfileSaveRequestValidator] validateRequestAgainstFetchedReferenceData() - END");
    }

    /**
     * 실제 조회 결과에서 요청 ID에 해당하는 참조를 확인하고 누락된 ID를 거절한다.
     * @param values 실제 조회된 ID별 정보
     * @param id 요청한 참조 ID
     * @return 조회된 참조 객체
     */
    private <T> T requireFetchedReference(Map<Long, T> values, Long id) {
        log.debug("[SpecProfileSaveRequestValidator] requireFetchedReference() - START | id: {}", id);
        T result = values.get(id);
        if (result == null) {
            throw new CustomException(SpecProfileErrorCode.REFERENCE_NOT_FOUND);
        }
        log.debug("[SpecProfileSaveRequestValidator] requireFetchedReference() - END | id: {}", id);
        return result;
    }

    /**
     * 잠금 조회한 CURRENT 프로필의 존재 여부를 검증한다.
     * @param profiles 회원의 CURRENT 잠금 조회 결과
     */
    public void validateCurrentProfileExists(List<SpecProfile> profiles) {
        log.debug("[SpecProfileSaveRequestValidator] validateCurrentProfileExists() - START");
        if (profiles.isEmpty()) {
            log.error("[SpecProfileSaveRequestValidator] 해당 회원에게 연결된 스펙 프로필이 존재하지 않습니다 | validateCurrentProfileExists() - END");
            throw new CustomException(SpecProfileErrorCode.CURRENT_NOT_FOUND);
        }
        log.debug("[SpecProfileSaveRequestValidator] validateCurrentProfileExists() - END");
    }

    /**
     * 회원에게 CURRENT 프로필이 정확히 하나만 연결되어 있는지 검증한다.
     * @param profiles 존재 여부 검증을 통과한 CURRENT 목록
     */
    public void validateSingleCurrentProfile(List<SpecProfile> profiles) {
        log.debug("[SpecProfileSaveRequestValidator] validateSingleCurrentProfile() - START");
        if (profiles.size() != 1) {
            log.error("[SpecProfileSaveRequestValidator] 해당 회원에게 연결된 초기 스펙 프로필이 2개 이상입니다 | validateSingleCurrentProfile() - END");
            throw new CustomException(SpecProfileErrorCode.INVALID_PROFILE_COUNT);
        }
        log.debug("[SpecProfileSaveRequestValidator] validateSingleCurrentProfile() - END");
    }

    /** 어학 마스터 데이터의 초기화 여부를 검증하며 초기화 API를 호출하지 않는다. */
    public void validateLanguageExamDataInitialized() {
        log.debug("[SpecProfileSaveRequestValidator] validateLanguageExamDataInitialized() - START");
        if (languageExamRepository.count() == 0) {
            log.error("[SpecProfileSaveRequestValidator] 어학 데이터 초기화가 필요합니다 | validateLanguageExamDataInitialized() - END");
            throw new CustomException(SpecProfileErrorCode.LANGUAGE_EXAM_NOT_INITIALIZED);
        }
        log.debug("[SpecProfileSaveRequestValidator] validateLanguageExamDataInitialized() - END");
    }

    /**
     * 한국 시간 기준으로 저장 요청의 날짜 및 항목별 조건을 검증한다.
     * @param request 저장할 전체 스펙
     */
    public void validateDatesAndDuplicateIdsAndConditionalFields(SpecProfileTemporarySaveReqDto request) {
        log.debug("[SpecProfileSaveRequestValidator] validateDatesAndDuplicateIdsAndConditionalFields() - START");
        validateDatesAndDuplicateIdsAndConditionalFields(request, LocalDate.now(ZoneId.of("Asia/Seoul")));
        log.debug("[SpecProfileSaveRequestValidator] validateDatesAndDuplicateIdsAndConditionalFields() - END");
    }

    /** 날짜 및 항목별 중복 식별자를 검증한다. */
    public void validateDatesAndDuplicateIdsAndConditionalFields(SpecProfileTemporarySaveReqDto request, LocalDate today) {
        // (1) 기준 날짜를 연월로 변환하고, 미래 출생년도를 거절한다.
        // today를 외부에서 받아 테스트에서도 동일한 기준 날짜를 사용할 수 있다.
        YearMonth month = YearMonth.from(today);
        require(request.birthYear() <= today.getYear());

        // (2) 한 요청에서 같은 기존 행을 여러 번 수정하지 않도록 하위 PK 중복을 검사한다.
        // 자격증과 어학 성적은 참조하는 자격증 ID·시험 ID의 중복도 별도로 검사한다.
        unique(request.academicAbilities(), AcademicAbilitySaveReqDto::academicAbilityId);
        unique(request.certificates(), CertificateSaveReqDto::specProfileCertificateId);
        unique(request.certificates(), CertificateSaveReqDto::certificateId);
        unique(request.languageExamScores(), LanguageExamScoreSaveReqDto::languageExamScoreId);
        unique(request.languageExamScores(), LanguageExamScoreSaveReqDto::languageExamId);
        unique(request.experiences(), ExperienceSaveReqDto::experienceId);

        // (3) 평점과 만점은 함께 입력하거나 함께 생략해야 한다.
        for (var academic : request.academicAbilities()) {
            require((academic.universityGrade() == null) == (academic.universityStandardGrade() == null));
            if (academic.universityStandardGrade() != null) {
                // 허용된 만점인지 숫자로 비교한다. compareTo는 4.0과 4.00을 같은 값으로 판단한다.
                require(List.of("4.0", "4.3", "4.5", "100").stream()
                        .anyMatch(v -> new BigDecimal(v).compareTo(academic.universityStandardGrade()) == 0));
                // 실제 평점은 입력한 만점을 초과할 수 없다.
                require(academic.universityGrade().compareTo(academic.universityStandardGrade()) <= 0);
            }
        }
        // (4) 어학 취득월은 현재 월 이하여야 하며, 만료월은 없거나 취득월 이상이어야 한다.
        // 숫자 점수·등급의 입력 조합은 별도 LanguageExamScoreValidator에서 검사한다.
        for (var score : request.languageExamScores()) {
            require(!score.acquiredYearMonth().isAfter(month));
            require(score.expiresYearMonth() == null
                    || !score.expiresYearMonth().isBefore(score.acquiredYearMonth()));
        }
        // (5) 활동 시작월은 현재 월 이하여야 한다.
        // 종료월 null은 진행 중을 의미하며, 종료월이 있으면 시작월보다 빠를 수 없다.
        for (var experience : request.experiences()) {
            require(!experience.startedYearMonth().isAfter(month));
            require(experience.endedYearMonth() == null
                    || !experience.endedYearMonth().isBefore(experience.startedYearMonth()));
            if (experience.awarded()) {
                // 수상한 활동은 기관명과 수상 등급이 필수이며, 빈 문자열·공백도 거절한다.
                require(experience.organizerName() != null && !experience.organizerName().isBlank());
                require(experience.awardGrade() != null && !experience.awardGrade().isBlank());
            } else {
                // 미수상 활동은 수상 관련 두 필드 모두 null이어야 한다.
                require(experience.organizerName() == null && experience.awardGrade() == null);
            }
        }
    }

    /** null이 아닌 항목 식별자의 중복을 거절한다. */
    private <T> void unique(List<T> values, Function<T, Long> key) {
        // 이미 등장한 식별자를 저장한다. T와 key를 사용해 서로 다른 DTO에 같은 검사를 적용한다.
        Set<Long> ids = new HashSet<>();
        for (T value : values) {
            // 전달받은 메서드 참조(예: CertificateSaveReqDto::certificateId)로 ID를 추출한다.
            Long id = key.apply(value);
            // null 식별자는 제외한다. 선택 PK의 null은 신규 생성 항목을 의미한다.
            // Set.add는 이미 존재하는 값이면 false이므로 중복을 감지할 수 있다.
            if (id != null && !ids.add(id)) {
                throw new CustomException(SpecProfileErrorCode.DUPLICATE_ID);
            }
        }
    }

    /** 요청 조건 위반을 공통 도메인 예외로 변환한다. */
    private void require(boolean valid) {
        // 각 검증 조건이 false이면 즉시 예외를 던져 이후 검증과 저장을 중단한다.
        if (!valid) {
            throw new CustomException(SpecProfileErrorCode.INVALID_INPUT);
        }
    }
}

