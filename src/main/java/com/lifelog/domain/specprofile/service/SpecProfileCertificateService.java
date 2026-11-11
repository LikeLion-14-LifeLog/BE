package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.certification.entity.Certification;
import com.lifelog.domain.specprofile.dto.CertificateSaveReqDto;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.entity.SpecProfileCertificate;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileMapper;
import com.lifelog.domain.specprofile.repository.SpecProfileCertificateRepository;
import com.lifelog.global.exception.CustomException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileCertificateService {
    private final SpecProfileCertificateRepository certificateRepository;
    private final SpecProfileMapper mapper;

    /**
     * 보유 자격증 PK를 기준으로 요청에서 빠진 행을 삭제하고 생성 또는 수정한다.
     * @param profile 소유 프로필
     * @param requests 저장할 전체 보유 자격증 목록
     * @param certificationsById 조회 및 검증된 마스터 참조 Map
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void syncCertificates(SpecProfile profile, List<CertificateSaveReqDto> requests, Map<Long, Certification> certificationsById) {
        log.info("[SpecProfileCertificateService] syncCertificates() - START | profileId: {}", profile.getId());
        /* (1) 본인 프로필의 보유 자격증을 조회하고 수정할 PK의 소유권을 확인한다. */
        List<SpecProfileCertificate> existingCertificates = certificateRepository.findAllBySpecProfileId(profile.getId());
        Map<Long, SpecProfileCertificate> existingSpecProfileCertificateById = existingCertificates.stream()
                .collect(Collectors.toMap(SpecProfileCertificate::getId, Function.identity()));
        Set<Long> requestedSpecProfileCertificateIdSet = requests.stream().map(CertificateSaveReqDto::specProfileCertificateId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> unownedRequestedIdSet = new HashSet<>(requestedSpecProfileCertificateIdSet);
        unownedRequestedIdSet.removeAll(existingSpecProfileCertificateById.keySet());
        if (!unownedRequestedIdSet.isEmpty()) {
            throw new CustomException(SpecProfileErrorCode.CHILD_NOT_FOUND);
        }

        /* (2) 요청 PK 목록에 없는 기존 보유 자격증을 삭제하고 반영한다. */
        List<SpecProfileCertificate> removedCertificates = existingCertificates.stream()
                .filter(specProfileCertificate -> !requestedSpecProfileCertificateIdSet.contains(specProfileCertificate.getId())).toList();
        certificateRepository.deleteAll(removedCertificates);
        certificateRepository.flush();

        /* (3) 신규 항목은 생성하고 기존 항목은 PK와 소유 프로필을 유지한다.
         * 자격증 참조와 취득월을 함께 덮어쓴다.
         */
        for (CertificateSaveReqDto request : requests) {
            Certification requestedCertification = certificationsById.get(request.certificateId());
            if (requestedCertification == null) {
                throw new CustomException(SpecProfileErrorCode.REFERENCE_NOT_FOUND);
            }
            SpecProfileCertificate mappedSpecProfileCertificate = mapper.toCertificate(request, profile, requestedCertification);
            if (request.specProfileCertificateId() == null) {
                certificateRepository.save(mappedSpecProfileCertificate);
            } else {
                SpecProfileCertificate specProfileCertificate = existingSpecProfileCertificateById.get(request.specProfileCertificateId());
                specProfileCertificate.update(mappedSpecProfileCertificate);
            }
        }
        log.info("[SpecProfileCertificateService] syncCertificates() - END | profileId: {}", profile.getId());
    }
}
