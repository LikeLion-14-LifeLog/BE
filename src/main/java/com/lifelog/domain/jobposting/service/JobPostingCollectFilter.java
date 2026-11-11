package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.dto.SeoulJobRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 수집할 공고를 고르는 조건을 한곳에 모은 클래스.
 * 현재: 6자리 모집직종코드(JOBS_CD)가 허용 접두어로 시작하는 공고만 저장
 * (기본 "13" = 정보통신 연구개발직 및 공학기술직, 인접 직군 제외).
 * 접두어는 job-posting.sync.job-code-prefixes 설정으로 바꿀 수 있다 (쉼표로 여러 개).
 */
@Component
public class JobPostingCollectFilter {

    private static final Pattern JOB_CODE = Pattern.compile("\\d{6}");

    private final List<String> jobCodePrefixes;

    public JobPostingCollectFilter(@Value("${job-posting.sync.job-code-prefixes:13}") List<String> jobCodePrefixes) {
        this.jobCodePrefixes = jobCodePrefixes.stream().map(String::strip).filter(p -> !p.isEmpty()).toList();
    }

    public boolean accepts(SeoulJobRow row) {
        String code = row.jobsCd() == null ? "" : row.jobsCd().strip();
        return JOB_CODE.matcher(code).matches()
                && jobCodePrefixes.stream().anyMatch(code::startsWith);
    }
}
