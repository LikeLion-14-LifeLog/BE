package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.domain.specprofile.dto.SpecProfileTemporarySaveReqDto;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.enums.SpecProfileType;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileMapper;
import com.lifelog.domain.specprofile.repository.SpecProfileRepository;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lifelog.domain.specprofile.dto.SpecProfileResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunResDto;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecProfileService {
    private final MemberRepository memberRepository;
    private final SpecProfileRepository profileRepository;
    private final SpecProfileMapper mapper;
    private final SpecProfileCreationPolicy creationPolicy;
    private final SpecProfileSaveService specProfileSaveService;
    private final SpecProfileQueryService specProfileQueryService;
    private final SpecProfileAnalyzationService specProfileAnalyzationService;

    /**
     * 본인의 CURRENT와 모든 저장 항목을 동일한 스냅샷에서 조회한다.
     * @param memberId JWT로 인증된 회원 ID
     * @return 미입력 값과 빈 목록을 포함한 현재 스펙
     */
    public SpecProfileResDto getCurrentProfile(Long memberId) {
        log.info("[SpecProfileService] getCurrentProfile() - START | memberId: {}", memberId);
        SpecProfileResDto result = specProfileQueryService.getCurrentProfile(memberId);
        log.info("[SpecProfileService] getCurrentProfile() - END | profileId: {}", result.specProfileId());
        return result;
    }

    /**
     * 연봉 분석 접수를 전담 서비스에 위임한다.
     * @param memberId 인증된 회원 ID
     * @return 분석 접수 응답
     */
    public SpecProfileAnalyzationRunResDto requestSpecProfileAnalyzationRun(Long memberId) {
        log.info("[SpecProfileService] 회원 스펙 연봉 예측 요청 시작 | requestSpecProfileAnalyzationRun() - START | memberId: {}", memberId);
        SpecProfileAnalyzationRunResDto result = specProfileAnalyzationService.requestRun(memberId);
        log.info("[SpecProfileService] 회원 스펙 연봉 예측 요청 시작 완료 | requestSpecProfileAnalyzationRun() - END | runId: {}", result.runId());
        return result;
    }

    /**
     * 회원 잠금 상태에서 기본 CURRENT를 생성하거나 기존 프로필을 반환한다.
     * @param member 가입한 회원
     * @return 회원의 유일한 CURRENT 프로필
     */
    @Transactional
    public SpecProfile createCurrentProfile(Member member) {
        log.info("[SpecProfileService] createCurrentProfile() - START | memberId: {}", member.getId());
        /* (1) 프로필이 없는 상태에서도 회원 잠금으로 동시 생성을 막는다. */
        Member locked = memberRepository.findByIdForUpdate(member.getId())
                .orElseThrow(() -> new CustomException(SpecProfileErrorCode.REFERENCE_NOT_FOUND));
        List<SpecProfile> profiles = profileRepository.findAllByMemberIdAndType(locked.getId(), SpecProfileType.CURRENT);
        if (profiles.size() > 1) {
            throw new CustomException(SpecProfileErrorCode.INVALID_PROFILE_COUNT);
        }
        /* (2) 기존 CURRENT를 유지하고 없는 회원에게만 기본 프로필을 생성한다. */
        SpecProfile result;
        if (profiles.isEmpty()) {
            creationPolicy.validate(SpecProfileType.CURRENT, 0);
            result = profileRepository.save(mapper.toCurrentProfile(locked));
        } else {
            result = profiles.getFirst();
        }
        log.info("[SpecProfileService] createCurrentProfile() - END | profileId: {}", result.getId());
        return result;
    }

    /**
     * 스펙 임시 저장을 전담 서비스에 위임한다.
     * @param memberId 인증된 회원 ID
     * @param request 저장할 전체 스펙
     */
    public void saveTemporary(Long memberId, SpecProfileTemporarySaveReqDto request) {
        log.info("[SpecProfileService] saveTemporary() - START | memberId: {}", memberId);
        /* (1) 저장 서비스의 트랜잭션에서 검증과 전체 목록 동기화를 수행한다. */
        specProfileSaveService.saveTemporary(memberId, request);
        log.info("[SpecProfileService] saveTemporary() - END | memberId: {}", memberId);
    }
}
