package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.languageexam.entity.LanguageExam;
import com.lifelog.domain.specprofile.dto.LanguageExamScoreSaveReqDto;
import com.lifelog.domain.specprofile.entity.LanguageExamScore;
import com.lifelog.domain.specprofile.entity.SpecProfile;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.domain.specprofile.mapper.SpecProfileMapper;
import com.lifelog.domain.specprofile.repository.LanguageExamScoreRepository;
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
public class LanguageExamScoreService {
    private final LanguageExamScoreRepository scoreRepository;
    private final SpecProfileMapper mapper;

    /**
     * 어학 성적 PK를 기준으로 요청에서 빠진 행을 삭제하고 생성 또는 수정한다.
     * @param profile 소유 프로필
     * @param requests 저장할 전체 어학 성적 목록
     * @param languageExamsById 조회 및 검증된 마스터 참조 Map
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void syncLanguageExamScores(SpecProfile profile, List<LanguageExamScoreSaveReqDto> requests, Map<Long, LanguageExam> languageExamsById) {
        log.info("[LanguageExamScoreService] syncLanguageExamScores() - START | profileId: {}", profile.getId());
        /* (1) 본인 프로필의 어학 성적을 조회하고 수정할 PK의 소유권을 확인한다. */
        List<LanguageExamScore> existingLanguageExamScores = scoreRepository.findAllBySpecProfileId(profile.getId());
        Map<Long, LanguageExamScore> existingLanguageExamScoreById = existingLanguageExamScores.stream()
                .collect(Collectors.toMap(LanguageExamScore::getId, Function.identity()));
        Set<Long> requestedLanguageExamScoreIdSet = requests.stream().map(LanguageExamScoreSaveReqDto::languageExamScoreId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> unownedRequestedIdSet = new HashSet<>(requestedLanguageExamScoreIdSet);
        unownedRequestedIdSet.removeAll(existingLanguageExamScoreById.keySet());
        if (!unownedRequestedIdSet.isEmpty()) {
            throw new CustomException(SpecProfileErrorCode.CHILD_NOT_FOUND);
        }

        /* (2) 요청 PK 목록에 없는 기존 어학 성적을 삭제하고 반영한다. */
        List<LanguageExamScore> removedLanguageExamScores = existingLanguageExamScores.stream()
                .filter(languageExamScore -> !requestedLanguageExamScoreIdSet.contains(languageExamScore.getId())).toList();
        scoreRepository.deleteAll(removedLanguageExamScores);
        scoreRepository.flush();

        /* (3) 신규 항목은 생성하고 기존 항목은 PK와 소유 프로필을 유지한다.
         * 시험 참조, 숫자 점수, 등급, 취득월과 만료월을 덮어쓰며 null도 반영한다.
         */
        for (LanguageExamScoreSaveReqDto request : requests) {
            LanguageExam requestedLanguageExam = languageExamsById.get(request.languageExamId());
            if (requestedLanguageExam == null) {
                throw new CustomException(SpecProfileErrorCode.REFERENCE_NOT_FOUND);
            }
            LanguageExamScore mappedLanguageExamScore = mapper.toLanguageScore(request, profile, requestedLanguageExam);
            if (request.languageExamScoreId() == null) {
                scoreRepository.save(mappedLanguageExamScore);
            } else {
                LanguageExamScore languageExamScore = existingLanguageExamScoreById.get(request.languageExamScoreId());
                languageExamScore.update(mappedLanguageExamScore);
            }
        }
        log.info("[LanguageExamScoreService] syncLanguageExamScores() - END | profileId: {}", profile.getId());
    }
}
