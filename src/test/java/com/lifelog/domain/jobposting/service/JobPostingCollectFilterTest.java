package com.lifelog.domain.jobposting.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lifelog.domain.jobposting.JobPostingFixtures.row;
import static org.assertj.core.api.Assertions.assertThat;

class JobPostingCollectFilterTest {

    private static final List<String> KEYWORD_CODES = List.of("135101", "135102");
    private static final List<String> KEYWORDS = List.of("데이터", "DB", "SQL", "AI", "ML", "LLM", "빅데이터", "분석");

    private final JobPostingCollectFilter filter = new JobPostingCollectFilter(List.of("13"), KEYWORD_CODES, KEYWORDS);

    @Test
    void 직종코드가_13으로_시작하면_수집() {
        assertThat(filter.accepts(row("A").jobsCd("133200").build())).isTrue();
        assertThat(filter.accepts(row("A").jobsCd("131100").build())).isTrue();
    }

    @Test
    void 인접_직군과_다른_직군은_제외() {
        assertThat(filter.accepts(row("A").jobsCd("415501").build())).isFalse();
        assertThat(filter.accepts(row("A").jobsCd("013700").build())).isFalse();
        assertThat(filter.accepts(row("A").jobsCd("550102").build())).isFalse();
    }

    @Test
    void 코드가_6자리_숫자가_아니면_제외() {
        assertThat(filter.accepts(row("A").jobsCd(null).build())).isFalse();
        assertThat(filter.accepts(row("A").jobsCd("").build())).isFalse();
        assertThat(filter.accepts(row("A").jobsCd("1332").build())).isFalse();
        assertThat(filter.accepts(row("A").jobsCd("13320A").build())).isFalse();
    }

    @Test
    void 접두어를_여러_개_지정할_수_있음() {
        JobPostingCollectFilter multi = new JobPostingCollectFilter(List.of("13", " 4155 "), KEYWORD_CODES, KEYWORDS);

        assertThat(multi.accepts(row("A").jobsCd("415501").build())).isTrue();
        assertThat(multi.accepts(row("A").jobsCd("612106").build())).isFalse();
    }

    @Test
    void 키워드가_필요한_코드는_제목에_키워드가_있을_때만_수집() {
        assertThat(filter.accepts(row("LLM·RAG 엔지니어 (NLP·AI 검색)").jobsCd("135101").build())).isTrue();
        assertThat(filter.accepts(row("빅데이터 플랫폼 개발자").jobsCd("135101").build())).isTrue();
        assertThat(filter.accepts(row("DB 운영 담당자").jobsCd("135102").build())).isTrue();
        assertThat(filter.accepts(row("데이터 라벨링 작업자").jobsCd("135102").build())).isTrue();
        assertThat(filter.accepts(row("SQL튜닝 경력자").jobsCd("135101").build())).isTrue();
    }

    @Test
    void 키워드가_필요한_코드에_키워드가_없으면_제외() {
        assertThat(filter.accepts(row("금형 설계관리").jobsCd("135101").build())).isFalse();
        assertThat(filter.accepts(row("자동차시트 금형설계 정규직사원 채용").jobsCd("135101").build())).isFalse();
        assertThat(filter.accepts(row("CAD CAM").jobsCd("135101").build())).isFalse();
    }

    @Test
    void 영문_키워드는_다른_단어의_일부면_인정하지_않음() {
        // "Email"의 ai, "HTML"의 ML은 키워드가 아님
        assertThat(filter.accepts(row("Email 마케팅 담당").jobsCd("135101").build())).isFalse();
        assertThat(filter.accepts(row("HTML 퍼블리셔").jobsCd("135101").build())).isFalse();
    }

    @Test
    void 키워드_조건은_지정한_코드에만_적용() {
        assertThat(filter.accepts(row("금형 설계관리").jobsCd("133200").build())).isTrue();
    }
}
