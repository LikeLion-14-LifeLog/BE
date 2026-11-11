package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.dto.SeoulJobRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 수집할 공고를 고르는 조건을 한곳에 모은 클래스. 설정은 job-posting.sync.* (쉼표로 여러 개)
 * 1) 6자리 모집직종코드(JOBS_CD)가 허용 접두어(job-code-prefixes, 기본 13)로 시작
 * 2) 원본 오분류가 많은 코드(keyword-required-job-codes, 기본 135101·135102)는
 *    제목에 키워드(required-title-keywords) 중 하나가 있을 때만 수집
 */
@Component
public class JobPostingCollectFilter {

    private static final Pattern JOB_CODE = Pattern.compile("\\d{6}");
    private static final Pattern ASCII_WORD = Pattern.compile("[A-Za-z]+");

    private final List<String> jobCodePrefixes;
    private final Set<String> keywordRequiredJobCodes;
    private final List<Pattern> requiredTitleKeywords;

    public JobPostingCollectFilter(
            @Value("${job-posting.sync.job-code-prefixes:13}") List<String> jobCodePrefixes,
            @Value("${job-posting.sync.keyword-required-job-codes:135101,135102}") List<String> keywordRequiredJobCodes,
            @Value("${job-posting.sync.required-title-keywords:데이터,DB,SQL,AI,ML,LLM,빅데이터,분석}") List<String> requiredTitleKeywords
    ) {
        this.jobCodePrefixes = trimAll(jobCodePrefixes);
        this.keywordRequiredJobCodes = Set.copyOf(trimAll(keywordRequiredJobCodes));
        this.requiredTitleKeywords = trimAll(requiredTitleKeywords).stream()
                .map(JobPostingCollectFilter::keywordPattern)
                .toList();
    }

    public boolean accepts(SeoulJobRow row) {
        String code = row.jobsCd() == null ? "" : row.jobsCd().strip();
        if (!JOB_CODE.matcher(code).matches() || jobCodePrefixes.stream().noneMatch(code::startsWith)) {
            return false;
        }
        if (keywordRequiredJobCodes.contains(code)) {
            String title = SeoulTextCleaner.clean(row.title());
            return title != null && requiredTitleKeywords.stream().anyMatch(p -> p.matcher(title).find());
        }
        return true;
    }

    // 영문 키워드는 다른 영단어 일부에 걸리지 않도록 앞뒤가 영문자가 아닐 때만, 한글은 포함 여부
    private static Pattern keywordPattern(String keyword) {
        String quoted = Pattern.quote(keyword);
        String regex = ASCII_WORD.matcher(keyword).matches()
                ? "(?<![A-Za-z])" + quoted + "(?![A-Za-z])"
                : quoted;
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static List<String> trimAll(List<String> values) {
        return values.stream().map(String::strip).filter(v -> !v.isEmpty()).collect(Collectors.toList());
    }
}
