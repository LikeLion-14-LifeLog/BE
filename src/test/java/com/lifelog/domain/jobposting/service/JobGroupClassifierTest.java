package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.entity.JobGroup;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class JobGroupClassifierTest {

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource(delimiter = '|', textBlock = """
            133200 | [비트컴퓨터] 신사업 풀스택 개발자              | FULLSTACK
            133200 | 제조 AI의 미래를 함께 만들어갈 풀스택 개발자    | FULLSTACK
            133200 | React 프론트엔드 개발자                        | FRONTEND
            133200 | Java 백엔드 개발자 채용 (물류 IT 솔루션)        | BACKEND
            133200 | 백엔드 개발자(SpacePass)                       | BACKEND
            135101 | 빅데이터 플랫폼 데이터 엔지니어                | DATA_ENGINEER
            135101 | LLM·RAG 엔지니어 (NLP·AI 검색)                 | AI_ML
            133204 | [모빌테크] AI Engineer 채용                    | AI_ML
            133900 | 소프트웨어 테스트 엔지니어 모집                | QA
            133900 | 신입 모바일 테스트 엔지니어 채용               | QA
            133900 | 서비스 운영 담당자                             | QA
            134200 | 정보 보안 전문가 구인광고                      | SECURITY
            134200 | 솔루션 기술지원 채용                           | SECURITY
            136103 | [신입] 보안솔루션 설치·유지보수 엔지니어       | SECURITY
            133102 | Rockchip 기반 임베디드 Linux 펌웨어            | EMBEDDED
            131100 | PLC, HMI, 자동제어시스템 기술팀 신입사원       | EMBEDDED
            136102 | 데이터센터 관제, 모니터링 정규직 채용          | DEVOPS_INFRA
            136103 | [IT]시스템 엔지니어, 네트워크 엔지니어 구인    | DEVOPS_INFRA
            137000 | 아이클라우드 성수센터 MCO 사원                 | DEVOPS_INFRA
            """)
    void 직종코드와_제목으로_그룹_판정(String jobCode, String title, JobGroup expected) {
        assertThat(JobGroupClassifier.classify(jobCode, title)).isEqualTo(expected);
    }

    // 136200(웹 운영자)은 개발 그룹 키워드 적용 범위(133·135) 밖이라 개발 그룹으로 잡히지 않음
    @ParameterizedTest
    @ValueSource(strings = {
            "웹디자인, 홈페이지 및 쇼핑몰 관리자 모집",
            "React 기반 홈페이지 관리자",
            "Java 백엔드 운영 담당 홈페이지 관리자",
            "풀스택 웹 운영자",
            "AI 마케팅 웹 운영자",
            "데이터 엔지니어 겸 웹 운영"
    })
    void 웹_운영자는_개발_그룹이_아님(String title) {
        assertThat(JobGroupClassifier.classify("136200", title))
                .isNotIn(JobGroup.BACKEND, JobGroup.FRONTEND, JobGroup.FULLSTACK, JobGroup.DATA_ENGINEER, JobGroup.AI_ML);
    }

    @ParameterizedTest(name = "{0} {1} → null")
    @CsvSource(delimiter = '|', textBlock = """
            133200 | 정규직 사원 모집
            131100 | 주차관제 H/W개발 경력자 모집
            132001 | 솔루션사업기획 / PM및 마케팅 담당자
            136200 | 홈페이지 및 스마트스토어 관리
            133200 | Email 마케팅 시스템 개발
            """)
    void 어느_그룹에도_해당하지_않으면_null(String jobCode, String title) {
        assertThat(JobGroupClassifier.classify(jobCode, title)).isNull();
    }
}
