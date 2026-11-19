package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.domain.specprofile.dto.SpecProfileTemporarySaveReqDto;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationReqDto;
import com.lifelog.domain.specprofile.dto.SpecProfileSimulationOutcome;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.enums.SpecProfileType;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileMapper;
import com.lifelog.domain.specprofile.policy.SpecProfileCreationPolicy;
import com.lifelog.domain.specprofile.repository.SpecProfileRepository;
import com.lifelog.domain.specprofile.service.specprofileanalyzation.SpecProfileAnalyzationResultQueryService;
import com.lifelog.domain.specprofile.service.specprofileanalyzation.SpecProfileAnalyzationService;
import com.lifelog.domain.specprofile.service.specprofileanalyzation.SpecProfileAnalyzationSseRegistry;
import com.lifelog.domain.specprofile.service.specprofileanalyzation.SpecProfileAnalyzationRunStateService;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunStopResDto;
import com.lifelog.domain.specprofile.service.specprofilesimulation.SpecProfileSimulationService;
import com.lifelog.domain.specprofile.service.specprofiletarget.SpecProfileTargetService;
import com.lifelog.domain.specprofile.dto.SpecProfileTargetSaveReqDto;
import com.lifelog.domain.specprofile.dto.SpecProfileTargetSaveResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileTargetResDto;
import com.lifelog.domain.specprofile.service.specprofiletarget.SpecProfileTargetQueryService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lifelog.domain.specprofile.dto.SpecProfileResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunLatestResDto;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationRunResultResDto;
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
    private final SpecProfileSimulationService specProfileSimulationService;
    private final SpecProfileTargetService specProfileTargetService;
    private final SpecProfileTargetQueryService specProfileTargetQueryService;
    private final SpecProfileQueryService specProfileQueryService;
    private final SpecProfileAnalyzationService specProfileAnalyzationService;
    private final SpecProfileAnalyzationResultQueryService specProfileAnalyzationResultQueryService;
    private final SpecProfileAnalyzationSseRegistry specProfileAnalyzationSseRegistry;
    private final SpecProfileAnalyzationRunStateService specProfileAnalyzationRunStateService;

    /**
     * 본인의 대기·진행·완료 상태 중 최신 분석 실행 ID 조회를 위임한다.
     * @param memberId 인증된 회원 ID
     * @return 최신 분석 실행 ID
     */
    public SpecProfileAnalyzationRunLatestResDto getLatestAnalyzationRun(Long memberId) {
        log.info("[SpecProfileService] 최신 분석 실행 조회 | getLatestAnalyzationRun() - START | memberId: {}", memberId);
        /* (1) 조회 서비스에서 소유권과 상태를 만족하는 최신 실행 ID를 가져온다. */
        SpecProfileAnalyzationRunLatestResDto result = specProfileAnalyzationService.getLatestRun(memberId);
        log.info("[SpecProfileService] 최신 분석 실행 조회 | getLatestAnalyzationRun() - END | runId: {}", result.runId());
        return result;
    }

    /**
     * 인증 회원이 선택한 목표의 저장된 추가 조건만 조회한다.
     * @param memberId 인증 회원 ID
     * @return 선택된 목표 또는 미선택 시 null
     */
    public SpecProfileTargetResDto getSelectedTarget(Long memberId) {
        log.info("[SpecProfileService] 선택 목표 조회 | getSelectedTarget() - START | memberId: {}", memberId);
        SpecProfileTargetResDto result = specProfileTargetQueryService.getSelectedTarget(memberId);
        log.info("[SpecProfileService] 선택 목표 조회 | getSelectedTarget() - END | selected: {}", result != null);
        return result;
    }

    /**
     * 사용자 TARGET 저장 및 미저장 AI 대안 비교를 전담 서비스에 위임한다.
     * @param memberId 인증 회원
     * @param request 사용자 목표
     * @return 화면의 선택지 세 개
     */
    public SpecProfileTargetSaveResDto saveTarget(Long memberId, SpecProfileTargetSaveReqDto request) {
        log.info("[SpecProfileService] 목표 저장 및 AI 비교 | saveTarget() - START | memberId: {}", memberId);
        SpecProfileTargetSaveResDto result = specProfileTargetService.saveTarget(memberId, request);
        log.info("[SpecProfileService] 목표 저장 및 AI 비교 | saveTarget() - END | targetId: {}", result.selectedTargetSpecProfileId());
        return result;
    }

    /**
     * 일회성 추가 조건의 연봉 계산을 시뮬레이션 서비스에 위임한다.
     * @param memberId 인증 회원 ID
     * @param request 목표 추가 조건
     * @return 현재·목표의 연차별 연봉 예측
     */
    public SpecProfileSimulationOutcome simulateAnnualSalary(Long memberId, SpecProfileSimulationReqDto request) {
        log.info("[SpecProfileService] 연봉 시뮬레이션 | simulateAnnualSalary() - START | memberId: {}", memberId);
        SpecProfileSimulationOutcome result = specProfileSimulationService.simulate(memberId, request);
        log.info("[SpecProfileService] 연봉 시뮬레이션 | simulateAnnualSalary() - END | profileId: {}", result.response().baseSpecProfileId());
        return result;
    }

    /**
     * 본인 실행의 중단을 커밋한 뒤 SSE 구독자에게 확정된 취소 상태를 전달한다.
     * @param memberId 인증 회원 ID
     * @param runId 중단할 실행 ID
     * @return 중단 상태와 최초 중단 시각
     */
    public SpecProfileAnalyzationRunStopResDto stopAnalyzationRun(Long memberId, Long runId) {
        log.info("[SpecProfileService] 분석 실행 중단 | stopAnalyzationRun() - START | memberId: {}, runId: {}", memberId, runId);
        /* (1) 독립 트랜잭션의 커밋이 완료된 응답을 받는다. */
        SpecProfileAnalyzationRunStopResDto result = specProfileAnalyzationRunStateService.stopOwnedRun(memberId, runId);
        /* (2) 통지 실패가 이미 확정된 중단의 성공 응답을 바꾸지 않도록 한다. */
        try {
            specProfileAnalyzationSseRegistry.canceled(runId);
        } catch (RuntimeException exception) {
            log.warn("[SpecProfileService] 중단 확정 후 SSE 취소 통지 실패 | runId: {}", runId, exception);
        }
        log.info("[SpecProfileService] 분석 실행 중단 | stopAnalyzationRun() - END | runId: {}", runId);
        return result;
    }

    /** 본인 분석 실행의 SSE 구독을 등록한다. @param memberId 인증 회원 @param runId 실행 ID @param lastEventId 마지막 이벤트 PK @return SSE 연결 */
    public SseEmitter subscribeAnalyzationEvents(Long memberId, Long runId, String lastEventId) {
        log.info("[SpecProfileService] 진행 상태 구독 | subscribeAnalyzationEvents() - START | memberId: {}, runId: {}", memberId, runId);
        SseEmitter result = specProfileAnalyzationSseRegistry.subscribe(memberId, runId, lastEventId);
        log.info("[SpecProfileService] 진행 상태 구독 | subscribeAnalyzationEvents() - END | runId: {}", runId);
        return result;
    }

    /**
     * 본인 분석 실행의 상태와 저장 결과 조회를 전담 서비스에 위임한다.
     * @param memberId 인증된 회원 ID
     * @param runId 조회할 분석 실행 ID
     * @return 실행 상태와 완료된 분석 결과
     */
    public SpecProfileAnalyzationRunResultResDto getAnalyzationRunResult(Long memberId, Long runId) {
        log.info("[SpecProfileService] 분석 실행 결과 조회 | getAnalyzationRunResult() - START | memberId: {}, runId: {}", memberId, runId);
        SpecProfileAnalyzationRunResultResDto result = specProfileAnalyzationResultQueryService.getRunResult(memberId, runId);
        log.info("[SpecProfileService] 분석 실행 결과 조회 | getAnalyzationRunResult() - END | runId: {}, status: {}", runId, result.status());
        return result;
    }

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
