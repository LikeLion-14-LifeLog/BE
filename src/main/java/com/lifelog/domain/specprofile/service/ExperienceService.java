package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.specprofile.dto.ExperienceSaveReqDto;
import com.lifelog.domain.specprofile.entity.Experience;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileMapper;
import com.lifelog.domain.specprofile.repository.ExperienceRepository;
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
public class ExperienceService {
    private final ExperienceRepository experienceRepository;
    private final SpecProfileMapper mapper;

    /**
     * 경험 PK를 기준으로 요청에서 빠진 행을 삭제하고 생성 또는 수정한다.
     * @param profile 소유 프로필
     * @param requests 저장할 전체 경험 목록
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void syncExperiences(SpecProfile profile, List<ExperienceSaveReqDto> requests) {
        log.info("[ExperienceService] syncExperiences() - START | profileId: {}", profile.getId());
        /* (1) 본인 프로필의 경험을 조회하고 수정할 PK의 소유권을 확인한다. */
        List<Experience> existingExperiences = experienceRepository.findAllBySpecProfileId(profile.getId());
        Map<Long, Experience> existingExperienceById = existingExperiences.stream()
                .collect(Collectors.toMap(Experience::getId, Function.identity()));
        Set<Long> requestedExperienceIdSet = requests.stream().map(ExperienceSaveReqDto::experienceId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> unownedRequestedIdSet = new HashSet<>(requestedExperienceIdSet);
        unownedRequestedIdSet.removeAll(existingExperienceById.keySet());
        if (!unownedRequestedIdSet.isEmpty()) {
            throw new CustomException(SpecProfileErrorCode.CHILD_NOT_FOUND);
        }

        /* (2) 요청 PK 목록에 없는 기존 경험을 삭제하고 반영한다. */
        List<Experience> removedExperiences = existingExperiences.stream()
                .filter(experience -> !requestedExperienceIdSet.contains(experience.getId())).toList();
        experienceRepository.deleteAll(removedExperiences);
        experienceRepository.flush();

        /* (3) 신규 항목은 생성하고 기존 항목은 PK와 소유 프로필을 유지한다.
         * 동일한 활동명도 별도 PK이면 유지하며 종료월과 수상 정보의 null도 반영한다.
         */
        for (ExperienceSaveReqDto request : requests) {
            Experience mappedExperience = mapper.toExperience(request, profile);
            if (request.experienceId() == null) {
                experienceRepository.save(mappedExperience);
            } else {
                Experience experience = existingExperienceById.get(request.experienceId());
                experience.update(mappedExperience);
            }
        }
        log.info("[ExperienceService] syncExperiences() - END | profileId: {}", profile.getId());
    }
}
