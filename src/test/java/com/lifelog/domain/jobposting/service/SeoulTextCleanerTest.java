package com.lifelog.domain.jobposting.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeoulTextCleanerTest {

    @Test
    void 업종명의_깨진_문자는_가운뎃점() {
        assertThat(SeoulTextCleaner.cleanIndustryName("사업시설 유지？관리 서비스업"))
                .isEqualTo("사업시설 유지·관리 서비스업");
        assertThat(SeoulTextCleaner.cleanIndustryName("건설 및 토목공사용 기계？장비 임대업"))
                .isEqualTo("건설 및 토목공사용 기계·장비 임대업");
    }

    @Test
    void 일반_텍스트의_깨진_문자는_공백으로_바꾸고_연속_공백은_한_칸() {
        assertThat(SeoulTextCleaner.clean("제조업 현장업무를？담당할？직원 모집합니다."))
                .isEqualTo("제조업 현장업무를 담당할 직원 모집합니다.");
        assertThat(SeoulTextCleaner.clean("？？ 행복한 수지요양원 요양보호사 채용 안내 ？？"))
                .isEqualTo("행복한 수지요양원 요양보호사 채용 안내");
    }

    @Test
    void 줄바꿈은_LF로_통일하고_유지() {
        assertThat(SeoulTextCleaner.clean("첫째 줄\r\n둘째 줄\r셋째 줄\n넷째 줄"))
                .isEqualTo("첫째 줄\n둘째 줄\n셋째 줄\n넷째 줄");
    }

    @Test
    void 줄_앞뒤_공백과_탭을_정리하고_빈_줄은_유지() {
        assertThat(SeoulTextCleaner.clean("관리자(1명)\n？\t통신망 및 프로토콜에 대한 이해  \n\n  다음 문단"))
                .isEqualTo("관리자(1명)\n통신망 및 프로토콜에 대한 이해\n\n다음 문단");
    }

    @Test
    void NBSP와_전각_공백도_한_칸() {
        assertThat(SeoulTextCleaner.clean("A  B　C")).isEqualTo("A B C");
    }

    @Test
    void 비었거나_깨진_문자만_있으면_null() {
        assertThat(SeoulTextCleaner.clean(null)).isNull();
        assertThat(SeoulTextCleaner.clean("   ")).isNull();
        assertThat(SeoulTextCleaner.clean("？？")).isNull();
        assertThat(SeoulTextCleaner.cleanIndustryName(null)).isNull();
    }
}
