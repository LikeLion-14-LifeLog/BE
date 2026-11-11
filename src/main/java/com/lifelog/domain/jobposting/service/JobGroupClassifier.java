package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.entity.JobGroup;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * jobGroup 판정 규칙을 한곳에 모은 클래스.
 * 1) 제목 키워드를 아래 순서대로 검사 (규칙마다 적용할 직종코드 범위가 있음), 처음 맞는 그룹
 * 2) 키워드가 없으면 직종코드로 판정
 * 3) 둘 다 없으면 null (필터 없는 전체 조회에서만 노출)
 * 개발 그룹(풀스택·프론트·백엔드·데이터·AI)은 133(소프트웨어 개발)·135(데이터) 코드에만 적용하므로
 * 136200(웹 운영자) 같은 운영 직무는 개발 그룹으로 잡히지 않음.
 */
final class JobGroupClassifier {

    private static final Set<String> DEV_SCOPE = Set.of("133", "135");
    private static final Set<String> ALL_IT = Set.of("13");

    private record Rule(JobGroup group, Set<String> scopePrefixes, Pattern titleKeywords, Set<String> jobCodes) {

        boolean matchesTitle(String jobCode, String title) {
            return scopePrefixes.stream().anyMatch(jobCode::startsWith) && titleKeywords.matcher(title).find();
        }
    }

    private static final List<Rule> RULES = List.of(
            rule(JobGroup.FULLSTACK, DEV_SCOPE, "풀스택|full[\\s-]?stack", Set.of()),
            rule(JobGroup.FRONTEND, DEV_SCOPE, "프론트|프런트|front[\\s-]?end|react(?!\\s*native)|vue|angular|퍼블리셔", Set.of()),
            rule(JobGroup.BACKEND, DEV_SCOPE, "백엔드|back[\\s-]?end|서버\\s?개발|java|spring|node\\.?js|php|django", Set.of()),
            rule(JobGroup.QA, ALL_IT, word("QA") + "|테스트|테스터", Set.of("133900")),
            // 135101은 원본에 금형설계 등 오분류가 많아 직종코드로 판정하지 않음
            rule(JobGroup.DATA_ENGINEER, DEV_SCOPE, "데이터\\s?엔지니어|data\\s?engineer|빅데이터|etl|dba|데이터베이스", Set.of()),
            rule(JobGroup.AI_ML, DEV_SCOPE, word("AI") + "|인공지능|머신러닝|딥러닝|machine\\s?learning|"
                    + word("ML") + "|" + word("LLM") + "|" + word("RAG") + "|" + word("NLP"), Set.of()),
            rule(JobGroup.SECURITY, ALL_IT, "보안|security", Set.of("134200", "134900")),
            rule(JobGroup.EMBEDDED, Set.of("131", "133"), "임베디드|펌웨어|firmware|f/w|plc|회로|제어", Set.of("133102")),
            // "관제"가 하드웨어 공고(주차관제 H/W 등)에 걸리지 않도록 운영 직무 코드로 범위 제한
            rule(JobGroup.DEVOPS_INFRA, Set.of("134", "136", "137"),
                    "인프라|devops|클라우드|서버\\s?구축|시스템\\s?엔지니어|네트워크\\s?엔지니어|관제",
                    Set.of("134100", "136101", "136102", "136103"))
    );

    private JobGroupClassifier() {
    }

    static JobGroup classify(String jobCode, String title) {
        String code = jobCode == null ? "" : jobCode;
        String text = title == null ? "" : title;
        for (Rule rule : RULES) {
            if (rule.matchesTitle(code, text)) {
                return rule.group();
            }
        }
        for (Rule rule : RULES) {
            if (rule.jobCodes().contains(code)) {
                return rule.group();
            }
        }
        return null;
    }

    private static Rule rule(JobGroup group, Set<String> scope, String keywords, Set<String> jobCodes) {
        return new Rule(group, scope,
                Pattern.compile(keywords, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), jobCodes);
    }

    // 영문 약어는 다른 영단어의 일부(예: "mail"의 ai)에 걸리지 않도록 앞뒤가 영문자가 아닐 때만
    private static String word(String keyword) {
        return "(?<![A-Za-z])" + keyword + "(?![A-Za-z])";
    }
}
