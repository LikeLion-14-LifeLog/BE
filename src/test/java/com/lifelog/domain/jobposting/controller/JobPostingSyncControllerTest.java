package com.lifelog.domain.jobposting.controller;

import com.lifelog.domain.company.repository.CompanyRepository;
import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.jobposting.config.JobPostingSyncExecutorConfig;
import com.lifelog.domain.jobposting.service.SeoulJobFetchResult;
import com.lifelog.domain.jobposting.service.SeoulJobPostingFetcher;
import com.lifelog.domain.jobposting.dto.SeoulJobRow;
import com.lifelog.domain.jobposting.entity.JobPosting;
import com.lifelog.domain.jobposting.entity.JobPostingStatus;
import com.lifelog.domain.jobposting.exception.JobPostingErrorCode;
import com.lifelog.domain.jobposting.repository.JobPostingRepository;
import com.lifelog.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import static com.lifelog.domain.jobposting.JobPostingFixtures.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "testuser", roles = "USER")
class JobPostingSyncControllerTest {

    private static final String SYNC_URL = "/api/admin/job-postings/sync";
    private static final String STATUS_URL = "/api/admin/job-postings/sync/status";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @MockitoBean
    private SeoulJobPostingFetcher fetcher;

    // 수집을 요청 스레드에서 바로 실행해 테스트 트랜잭션 안에서 결과를 확인 (롤백 가능)
    @TestBean(name = JobPostingSyncExecutorConfig.EXECUTOR_BEAN)
    private Executor jobPostingSyncExecutor;

    static Executor jobPostingSyncExecutor() {
        return Runnable::run;
    }

    @BeforeEach
    void setUp() {
        jobPostingRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void 수집_시작은_202이고_상태와_결과를_반환() throws Exception {
        given(fetcher.fetch()).willReturn(complete(row("A").build(), row("B").build()));

        mockMvc.perform(post(SYNC_URL).with(admin()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.finishedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.errorCode").value(nullValue()))
                .andExpect(jsonPath("$.data.result.totalCount").value(2))
                .andExpect(jsonPath("$.data.result.createdCount").value(2))
                .andExpect(jsonPath("$.data.result.updatedCount").value(0))
                .andExpect(jsonPath("$.data.result.unchangedCount").value(0))
                .andExpect(jsonPath("$.data.result.closedCount").value(0));

        assertThat(jobPostingRepository.count()).isEqualTo(2);
        // 같은 이름의 기업은 한 번만 생성
        assertThat(companyRepository.findAll())
                .singleElement()
                .satisfies(company -> assertThat(company.getIndustryName()).isEqualTo("자동차 재제조 부품 제조업"));
    }

    @Test
    void 비로그인이면_401() throws Exception {
        mockMvc.perform(post(SYNC_URL).with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401"));

        verify(fetcher, never()).fetch();
    }

    @Test
    void 일반회원이면_403이고_수집하지_않음() throws Exception {
        mockMvc.perform(post(SYNC_URL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));

        verify(fetcher, never()).fetch();
        assertThat(jobPostingRepository.count()).isZero();
    }

    @Test
    void 같은_원본을_다시_수집하면_변경_없음() throws Exception {
        given(fetcher.fetch()).willReturn(complete(row("A").build(), row("B").build()));
        sync();

        sync()
                .andExpect(jsonPath("$.data.result.createdCount").value(0))
                .andExpect(jsonPath("$.data.result.unchangedCount").value(2));
        assertThat(jobPostingRepository.count()).isEqualTo(2);
    }

    @Test
    void 내용이_바뀌면_수정하고_ID는_유지() throws Exception {
        given(fetcher.fetch()).willReturn(complete(row("A").build(), row("B").build()));
        sync();
        Long id = findByTitle("A").getId();

        given(fetcher.fetch()).willReturn(complete(
                row("A").jobCont("업무 변경").salTpNm("월급3,000,000원 이상,").build(),
                row("B").build()));

        sync()
                .andExpect(jsonPath("$.data.result.updatedCount").value(1))
                .andExpect(jsonPath("$.data.result.unchangedCount").value(1));

        JobPosting updated = findByTitle("A");
        assertThat(updated.getId()).isEqualTo(id);
        assertThat(updated.getDescription()).isEqualTo("업무 변경");
        assertThat(updated.getPays()).singleElement()
                .satisfies(pay -> assertThat(pay.getAmountMin()).isEqualTo(3_000_000L));
    }

    @Test
    void 원본에서_사라진_공고는_마감() throws Exception {
        given(fetcher.fetch()).willReturn(complete(row("A").build(), row("B").build()));
        sync();

        given(fetcher.fetch()).willReturn(complete(row("A").build()));

        sync()
                .andExpect(jsonPath("$.data.result.totalCount").value(1))
                .andExpect(jsonPath("$.data.result.unchangedCount").value(1))
                .andExpect(jsonPath("$.data.result.closedCount").value(1));
        assertThat(findByTitle("B").getStatus()).isEqualTo(JobPostingStatus.CLOSE);
        assertThat(findByTitle("A").getStatus()).isEqualTo(JobPostingStatus.OPEN);
    }

    @Test
    void 마감일이_지난_공고는_마감으로_저장하고_모집중이던_공고는_closed로_집계() throws Exception {
        given(fetcher.fetch()).willReturn(complete(row("A").build(), row("B").closeDt("20-01-01").build()));
        sync().andExpect(jsonPath("$.data.result.createdCount").value(2));
        assertThat(findByTitle("B").getStatus()).isEqualTo(JobPostingStatus.CLOSE);

        given(fetcher.fetch()).willReturn(complete(row("A").closeDt("20-01-01").build(), row("B").closeDt("20-01-01").build()));
        sync()
                .andExpect(jsonPath("$.data.result.closedCount").value(1))
                .andExpect(jsonPath("$.data.result.unchangedCount").value(1));
        assertThat(findByTitle("A").getStatus()).isEqualTo(JobPostingStatus.CLOSE);
    }

    @Test
    void 마감됐던_공고가_다시_나오면_모집중으로_수정() throws Exception {
        given(fetcher.fetch()).willReturn(complete(row("A").build(), row("B").build()));
        sync();
        given(fetcher.fetch()).willReturn(complete(row("A").build()));
        sync();

        given(fetcher.fetch()).willReturn(complete(row("A").build(), row("B").build()));

        sync().andExpect(jsonPath("$.data.result.updatedCount").value(1));
        assertThat(findByTitle("B").getStatus()).isEqualTo(JobPostingStatus.OPEN);
    }

    @Test
    void 원본에_중복_공고가_있으면_한_건만_저장() throws Exception {
        given(fetcher.fetch()).willReturn(complete(row("A").build(), row("A").build()));

        sync()
                .andExpect(jsonPath("$.data.result.totalCount").value(1))
                .andExpect(jsonPath("$.data.result.createdCount").value(1));
    }

    @Test
    void 변환에_실패한_공고는_마감하지_않고_기존_데이터를_유지() throws Exception {
        given(fetcher.fetch()).willReturn(complete(row("A").build(), row("B").build()));
        sync();

        // regionCd가 없으면 변환 실패. 해시 기준(기업명·제목·등록일·근무지)은 같음
        given(fetcher.fetch()).willReturn(complete(
                row("A").build(),
                row("B").regionCd(null).jobCont("바뀐 내용").build()));

        sync()
                .andExpect(jsonPath("$.data.result.totalCount").value(1))
                .andExpect(jsonPath("$.data.result.unchangedCount").value(1))
                .andExpect(jsonPath("$.data.result.updatedCount").value(0))
                .andExpect(jsonPath("$.data.result.closedCount").value(0));

        JobPosting kept = findByTitle("B");
        assertThat(kept.getStatus()).isEqualTo(JobPostingStatus.OPEN);
        assertThat(kept.getDescription()).isEqualTo("자동차종합검사 업무");
    }

    @Test
    void 변환에_실패한_새_공고는_저장하지_않음() throws Exception {
        given(fetcher.fetch()).willReturn(complete(row("A").build(), row("B").regionCd(null).build()));

        sync().andExpect(jsonPath("$.data.result.createdCount").value(1));
        assertThat(jobPostingRepository.count()).isEqualTo(1);
    }

    @Test
    void 외부_API_호출이_실패하면_FAILED와_에러코드이고_저장_마감_모두_하지_않음() throws Exception {
        given(fetcher.fetch()).willReturn(complete(row("A").build(), row("B").build()));
        sync();

        willThrow(new CustomException(JobPostingErrorCode.EXTERNAL_API_FAILED)).given(fetcher).fetch();

        mockMvc.perform(post(SYNC_URL).with(admin()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorCode").value("JOB_POSTING_502"))
                .andExpect(jsonPath("$.data.result").value(nullValue()));
        mockMvc.perform(get(STATUS_URL).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorCode").value("JOB_POSTING_502"));
        assertThat(jobPostingRepository.count()).isEqualTo(2);
        assertThat(findByTitle("A").getStatus()).isEqualTo(JobPostingStatus.OPEN);
        assertThat(findByTitle("B").getStatus()).isEqualTo(JobPostingStatus.OPEN);
    }

    @Test
    void 원본이_0건이면_마감하지_않고_모두_0() throws Exception {
        given(fetcher.fetch()).willReturn(complete(row("A").build()));
        sync();

        given(fetcher.fetch()).willReturn(complete());

        sync()
                .andExpect(jsonPath("$.data.result.totalCount").value(0))
                .andExpect(jsonPath("$.data.result.closedCount").value(0))
                .andExpect(jsonPath("$.data.result.complete").value(false));
        assertThat(findByTitle("A").getStatus()).isEqualTo(JobPostingStatus.OPEN);
    }

    @Test
    void 전체보다_적게_받으면_받은_공고만_저장하고_마감은_건너뜀() throws Exception {
        given(fetcher.fetch()).willReturn(complete(row("A").build(), row("B").build()));
        sync().andExpect(jsonPath("$.data.result.complete").value(true));

        given(fetcher.fetch()).willReturn(incomplete(
                row("A").jobCont("업무 변경").build(), row("C").build()));

        sync()
                .andExpect(jsonPath("$.data.result.createdCount").value(1))
                .andExpect(jsonPath("$.data.result.updatedCount").value(1))
                .andExpect(jsonPath("$.data.result.closedCount").value(0))
                .andExpect(jsonPath("$.data.result.complete").value(false));
        assertThat(findByTitle("A").getDescription()).isEqualTo("업무 변경");
        assertThat(findByTitle("B").getStatus()).isEqualTo(JobPostingStatus.OPEN);
        assertThat(findByTitle("C").getStatus()).isEqualTo(JobPostingStatus.OPEN);
    }

    @Test
    void 수집하면_직종코드_앞_4자리로_직무에_연결() throws Exception {
        Job job = jobRepository.saveAndFlush(Job.builder()
                .mainCategoryCode("1").mainCategoryName("연구직 및 공학 기술직")
                .middleCategoryCode("13").middleCategoryName("정보통신 연구개발직 및 공학기술직")
                .subCategoryCode("133").subCategoryName("소프트웨어 개발자")
                .code("1332").name("응용 소프트웨어 개발자")
                .build());
        given(fetcher.fetch()).willReturn(complete(row("Java 백엔드 개발자").jobsCd("133200").build()));

        sync().andExpect(jsonPath("$.data.result.createdCount").value(1));

        JobPosting saved = findByTitle("Java 백엔드 개발자");
        assertThat(saved.getJobs()).singleElement()
                .satisfies(link -> assertThat(link.getJob().getId()).isEqualTo(job.getId()));

        // 같은 원본을 다시 수집하면 직무 연결도 그대로라 변경 없음
        sync().andExpect(jsonPath("$.data.result.unchangedCount").value(1));
    }

    @Test
    void 직무_데이터에_없는_코드는_공고만_저장하고_직무는_연결하지_않음() throws Exception {
        given(fetcher.fetch()).willReturn(complete(row("A").jobsCd("999901").build()));

        sync().andExpect(jsonPath("$.data.result.createdCount").value(1));
        assertThat(findByTitle("A").getJobs()).isEmpty();
    }

    @Test
    void IT가_아닌_직군도_모두_수집() throws Exception {
        given(fetcher.fetch()).willReturn(complete(
                row("백엔드 개발자").jobsCd("133200").build(),
                row("재가 요양보호사").jobsCd("550102").build(),
                row("웹디자이너").jobsCd("415501").build(),
                row("금형 설계관리").jobsCd("135101").build()));

        sync()
                .andExpect(jsonPath("$.data.result.totalCount").value(4))
                .andExpect(jsonPath("$.data.result.createdCount").value(4));
        assertThat(jobPostingRepository.findAll())
                .extracting(JobPosting::getTitle)
                .containsExactlyInAnyOrder("백엔드 개발자", "재가 요양보호사", "웹디자이너", "금형 설계관리");
    }

    @Test
    void 상태_조회는_마지막_실행_상태와_결과() throws Exception {
        given(fetcher.fetch()).willReturn(complete(row("A").build()));
        sync();

        mockMvc.perform(get(STATUS_URL).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.createdCount").value(1));
    }

    @Test
    void 상태_조회도_ADMIN만() throws Exception {
        mockMvc.perform(get(STATUS_URL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
        mockMvc.perform(get(STATUS_URL).with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 건너뛴_공고_수를_skippedCount로_반환() throws Exception {
        String unreadable = "읽지 못한 공고의 externalId";
        given(fetcher.fetch()).willReturn(new SeoulJobFetchResult(
                List.of(row("A").build(), row("B").regionCd(null).build()), Set.of(unreadable), true));

        sync()
                .andExpect(jsonPath("$.data.result.totalCount").value(1))
                .andExpect(jsonPath("$.data.result.skippedCount").value(2))
                .andExpect(jsonPath("$.data.result.complete").value(true));
    }

    private static SeoulJobFetchResult complete(SeoulJobRow... rows) {
        return new SeoulJobFetchResult(List.of(rows), Set.of(), true);
    }

    private static SeoulJobFetchResult incomplete(SeoulJobRow... rows) {
        return new SeoulJobFetchResult(List.of(rows), Set.of(), false);
    }

    // 동기 실행기라 202 응답 시점에 이미 끝나 있음
    private ResultActions sync() throws Exception {
        return mockMvc.perform(post(SYNC_URL).with(admin()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }

    private JobPosting findByTitle(String title) {
        return jobPostingRepository.findAll().stream()
                .filter(posting -> posting.getTitle().equals(title))
                .findFirst()
                .orElseThrow();
    }

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }
}
