package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.specprofile.dto.AcademicAbilitySaveReqDto;
import com.lifelog.domain.specprofile.entity.AcademicAbility;
import com.lifelog.domain.specprofile.entity.AcademicAbilityMajor;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.repository.AcademicAbilityRepository;
import com.lifelog.domain.specprofile.repository.AcademicAbilityMajorRepository;
import com.lifelog.domain.specprofile.mapper.SpecProfileMapper;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.global.exception.CustomException;
import java.util.ArrayList;
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
public class AcademicAbilityService {
    private final AcademicAbilityRepository academicRepository;
    private final AcademicAbilityMajorRepository academicMajorRepository;
    private final SpecProfileMapper mapper;

    /**
     * 새롭게 학력 및 전공 데이터와 기존에 존재하던 학력 및 전공 데이터를 신규 데이터 기준으로 동기화 설정한다.
     * @param profile 소유 프로필
     * @param academicAbilityRequests 저장할 전체 학력 목록
     * @param majorById 조회 및 검증된 전공 Map
     * */
    @Transactional(propagation = Propagation.MANDATORY)
    public void syncAcademicAbilities(SpecProfile profile, List<AcademicAbilitySaveReqDto> academicAbilityRequests,
                                       Map<Long, Major> majorById) {
        log.info("[AcademicAbilityService] syncAcademicAbilities() - START");
        /*
         * (1) 기존 학력 조회
         * 프로필에 연결된 학력을 조회하고, PK로 기존 행을 찾을 수 있도록 Map을 구성한다.
         */
        List<AcademicAbility> existingAcademicAbilities = academicRepository.findAllBySpecProfileId(profile.getId());
        Map<Long, AcademicAbility> existingAcademicAbilityMapById = existingAcademicAbilities.stream()
                .collect(Collectors.toMap(AcademicAbility::getId, Function.identity()));
        /*
         * (2) 요청한 기존 학력의 소유권 검증
         * 신규 항목의 null PK는 제외하고, 나머지 PK가 모두 본인 프로필에 속하는지 확인한다.
         * 입력받은 PK 중 하나라도 본인 Spec Profile에 속하지 않는 학력 PK인 경우 예외를 반환한다.
         */
        Set<Long> requestedAcademicAbilityIdSet = academicAbilityRequests.stream()
                .map(AcademicAbilitySaveReqDto::academicAbilityId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> unownedAcademicAbilityIdSet = new HashSet<>(requestedAcademicAbilityIdSet);
        unownedAcademicAbilityIdSet.removeAll(existingAcademicAbilityMapById.keySet());
        if (!unownedAcademicAbilityIdSet.isEmpty()) {
            throw new CustomException(SpecProfileErrorCode.CHILD_NOT_FOUND);
        }
        /*
         * (3) 기존 전공 연결 및 삭제 대상 조회
         * 기존 학력의 전공 연결을 조회하고, 요청 PK 목록에서 빠진 학력을 삭제 대상으로 분류한다.
         */
        List<AcademicAbilityMajor> existingAcademicAbilityMajors = existingAcademicAbilities.isEmpty() ? List.of()
                : academicMajorRepository.findAllByAcademicAbilityIdIn(new ArrayList<>(existingAcademicAbilityMapById.keySet()));
        Map<Long, List<AcademicAbilityMajor>> academicAbilityMajorsByAcademicAbilityId = existingAcademicAbilityMajors.stream()
                .collect(Collectors.groupingBy(link -> link.getAcademicAbility().getId()));
        List<AcademicAbility> removedAcademicAbilities = existingAcademicAbilities.stream()
                .filter(academicAbility -> !requestedAcademicAbilityIdSet.contains(academicAbility.getId())).toList();
        Set<Long> removedAcademicAbilityIdSet = removedAcademicAbilities.stream()
                .map(AcademicAbility::getId).collect(Collectors.toSet());
        /*
         * (4) 누락된 학력 삭제
         * FK 참조 순서에 맞춰 전공 연결을 먼저 삭제·반영한 뒤 학력을 삭제·반영한다.
         */
        academicMajorRepository.deleteAll(existingAcademicAbilityMajors.stream()
                .filter(academicAbilityMajor -> removedAcademicAbilityIdSet.contains(
                        academicAbilityMajor.getAcademicAbility().getId())).toList());
        academicMajorRepository.flush();
        academicRepository.deleteAll(removedAcademicAbilities);
        academicRepository.flush();
        /*
         * (5) 요청 학력 생성 또는 수정
         * PK가 없으면 신규 생성하고, 있으면 기존 PK와 소유 프로필을 유지하며 입력값을 덮어쓴다.
         */
        for (AcademicAbilitySaveReqDto academicAbilityRequest : academicAbilityRequests) {
            AcademicAbility mappedAcademicAbility = mapper.toAcademicAbility(academicAbilityRequest, profile);
            AcademicAbility academicAbility;
            if (academicAbilityRequest.academicAbilityId() == null) {
                academicAbility = academicRepository.save(mappedAcademicAbility);
            } else {
                academicAbility = existingAcademicAbilityMapById.get(academicAbilityRequest.academicAbilityId());
                academicAbility.update(mappedAcademicAbility);
            }
            /*
             * (6) 학력별 전공 연결 동기화
             * 연결이 없으면 생성하고, 하나면 기존 연결의 전공을 변경한다.
             * 단일 전공 요청으로 처리할 수 없는 복수 연결은 예외로 거절하여 전체 저장을 롤백한다.
             */
            List<AcademicAbilityMajor> academicAbilityMajors = academicAbilityMajorsByAcademicAbilityId
                    .getOrDefault(academicAbility.getId(), List.of());
            Major requestedMajor = majorById.get(academicAbilityRequest.majorId());
            if (requestedMajor == null) {
                throw new CustomException(SpecProfileErrorCode.REFERENCE_NOT_FOUND);
            }
            if (academicAbilityMajors.isEmpty()) {
                academicMajorRepository.save(mapper.toAcademicAbilityMajor(academicAbility, requestedMajor));
            } else if (academicAbilityMajors.size() == 1) {
                academicAbilityMajors.getFirst().changeMajor(requestedMajor);
            } else {
                throw new CustomException(SpecProfileErrorCode.INVALID_INPUT);
            }
        }
        log.info("[AcademicAbilityService] syncAcademicAbilities() - END");
    }

}
