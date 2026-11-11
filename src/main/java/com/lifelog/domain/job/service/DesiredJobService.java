package com.lifelog.domain.job.service;

import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.job.entity.DesiredJob;
import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.DesiredJobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DesiredJobService {
    private final DesiredJobRepository desiredJobRepository;

    /**
     * 회원에 연결된 희망 직무를 수정하거나 생성한다.
     * @param member 희망 직무를 지정할 회원
     * @param job 조회 및 검증된 직무
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void syncDesiredJob(Member member, Job job) {
        log.info("[DesiredJobService] syncDesiredJob() - START");
        /* (1) 회원당 하나의 희망 직무를 유지하며 직무 참조를 변경한다. */
        desiredJobRepository.findByMemberId(member.getId()).ifPresentOrElse(
                desired -> desired.changeJob(job),
                () -> desiredJobRepository.save(DesiredJob.builder().member(member).job(job).build()));
        log.info("[DesiredJobService] syncDesiredJob() - END");
    }

}
