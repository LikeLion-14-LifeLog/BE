package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.residentialarea.enums.ResidentialArea;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileBasicInfoService {

    /**
     * 기존 프로필의 기본 정보를 변경하며 식별 정보는 유지한다.
     * @param profile CURRENT 잠금을 획득한 프로필
     * @param birthYear 출생년도
     * @param residenceRegion 거주지역
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateBasicInfo(SpecProfile profile, Integer birthYear, ResidentialArea residenceRegion) {
        log.info("[SpecProfileBasicInfoService] updateBasicInfo() - START | profileId: {}", profile.getId());
        /* (1) 관리 중인 프로필의 기본 정보를 갱신하고 변경 감지로 반영한다. */
        profile.updateTemporaryInfo(birthYear, residenceRegion);
        log.info("[SpecProfileBasicInfoService] updateBasicInfo() - END | profileId: {}", profile.getId());
    }
}
