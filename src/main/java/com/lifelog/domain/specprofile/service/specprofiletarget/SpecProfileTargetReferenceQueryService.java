package com.lifelog.domain.specprofile.service.specprofiletarget;

import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.certification.repository.CertificationRepository;
import com.lifelog.domain.languageexam.entity.LanguageExam;
import com.lifelog.domain.languageexam.repository.LanguageExamRepository;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.domain.major.repository.MajorRepository;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationSnapshot;
import com.lifelog.domain.specprofile.dto.SpecProfileTargetReferenceResDto;
import com.lifelog.domain.specprofile.mapper.SpecProfileTargetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileTargetReferenceQueryService {
    private final MajorRepository majorRepository;
    private final CertificationRepository certificateRepository;
    private final LanguageExamRepository examRepository;
    private final SpecProfileTargetMapper mapper;

    /**
     * 실제 ID를 가진 마스터 후보를 현재·목표 전공과 희망 직무 검색어를 우선해 구성한다.
     * @param base 요청 CURRENT
     * @param target 사용자 목표
     * @return 입력 한도를 고려한 추천 참조 목록
     */
    @Transactional(readOnly = true)
    public SpecProfileTargetReferenceResDto findAllowedReferences(SpecProfileAnalyzationSnapshot base, SpecProfileAnalyzationSnapshot target) {
        log.info("[SpecProfileTargetReferenceQueryService] 추천 참조 조회 | findAllowedReferences() - START | profileId: {}", base.specProfileId());
        /* (1) 현재·목표 전공을 우선하고 같은 계열의 추가 전공을 조회한다. */
        Map<Long, Major> majors = new LinkedHashMap<>();
        majorRepository.findAllById(target.academicAbilities().stream().map(value -> value.majorId()).toList())
                .forEach(value -> majors.put(value.getId(), value));
        for (String keyword : target.academicAbilities().stream().map(value -> value.majorName()).distinct().toList()) {
            majorRepository.searchMajors(keyword, null, PageRequest.of(0, 30, Sort.by("id")))
                    .forEach(value -> majors.putIfAbsent(value.getId(), value));
        }
        Set<MajorMainField> fields = target.academicAbilities().stream().map(value -> value.majorAffiliation()).collect(Collectors.toSet());
        for (MajorMainField field : fields.stream().sorted().toList()) {
            majorRepository.findByMainField(field, PageRequest.of(0, 100, Sort.by("id")))
                    .forEach(value -> majors.putIfAbsent(value.getId(), value));
        }
        /* (2) 직무 이름으로 찾은 자격증과 실제 목표 참조를 우선하고 공통 후보를 보완한다. */
        Map<Long, Certification> certificates = new LinkedHashMap<>();
        for (String keyword : base.jobName().split("[\\s·/]+")) {
            if (keyword.length() < 2) continue;
            certificateRepository.searchCertifications(keyword, null, null, PageRequest.of(0, 50, Sort.by("id")))
                    .forEach(value -> certificates.putIfAbsent(value.getId(), value));
        }
        certificateRepository.findAllById(target.certificates().stream().map(value -> value.certificateId()).toList())
                .forEach(value -> certificates.putIfAbsent(value.getId(), value));
        certificateRepository.findAll(PageRequest.of(0, 200, Sort.by("id")))
                .forEach(value -> certificates.putIfAbsent(value.getId(), value));
        Set<Long> ownedCertificates = base.certificates().stream().map(value -> value.certificateId()).collect(Collectors.toSet());
        ownedCertificates.forEach(certificates::remove);
        Set<Long> ownedExams = base.languageExamScores().stream().map(value -> value.languageExamId()).collect(Collectors.toSet());
        List<LanguageExam> exams = examRepository.findAll(Sort.by("id")).stream()
                .filter(value -> !ownedExams.contains(value.getId())).toList();
        SpecProfileTargetReferenceResDto result = mapper.toReferences(majors.values(), certificates.values(), exams);
        log.info("[SpecProfileTargetReferenceQueryService] 추천 참조 조회 | findAllowedReferences() - END | majors: {}, certificates: {}, exams: {}",
                result.majors().size(), result.certificates().size(), result.languageExams().size());
        return result;
    }
}
