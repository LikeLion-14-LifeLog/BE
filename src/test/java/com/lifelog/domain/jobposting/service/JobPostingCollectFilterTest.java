package com.lifelog.domain.jobposting.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lifelog.domain.jobposting.JobPostingFixtures.row;
import static org.assertj.core.api.Assertions.assertThat;

class JobPostingCollectFilterTest {

    private final JobPostingCollectFilter filter = new JobPostingCollectFilter(List.of("13"));

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
        JobPostingCollectFilter multi = new JobPostingCollectFilter(List.of("13", " 4155 "));

        assertThat(multi.accepts(row("A").jobsCd("415501").build())).isTrue();
        assertThat(multi.accepts(row("A").jobsCd("415502").build())).isTrue();
        assertThat(multi.accepts(row("A").jobsCd("612106").build())).isFalse();
    }
}
