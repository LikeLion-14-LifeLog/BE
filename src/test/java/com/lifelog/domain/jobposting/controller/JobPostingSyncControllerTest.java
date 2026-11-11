package com.lifelog.domain.jobposting.controller;

import com.lifelog.domain.company.repository.CompanyRepository;
import com.lifelog.domain.jobposting.client.SeoulJobApiClient;
import com.lifelog.domain.jobposting.client.SeoulJobRow;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.lifelog.domain.jobposting.JobPostingFixtures.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "testuser", roles = "USER")
class JobPostingSyncControllerTest {

    private static final String SYNC_URL = "/api/admin/job-postings/sync";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @MockitoBean
    private SeoulJobApiClient seoulJobApiClient;

    @BeforeEach
    void setUp() {
        jobPostingRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void 첫_수집_응답_형식() throws Exception {
        given(seoulJobApiClient.fetchAll()).willReturn(List.of(row("A").build(), row("B").build()));

        mockMvc.perform(post(SYNC_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.createdCount").value(2))
                .andExpect(jsonPath("$.data.updatedCount").value(0))
                .andExpect(jsonPath("$.data.unchangedCount").value(0))
                .andExpect(jsonPath("$.data.closedCount").value(0));

        assertThat(jobPostingRepository.count()).isEqualTo(2);
        // 같은 이름의 기업은 한 번만 생성
        assertThat(companyRepository.findAll())
                .singleElement()
                .satisfies(company -> assertThat(company.getIndustryName()).isEqualTo("자동차 재제조 부품 제조업"));
    }

    @Test
    void 같은_원본을_다시_수집하면_변경_없음() throws Exception {
        given(seoulJobApiClient.fetchAll()).willReturn(List.of(row("A").build(), row("B").build()));
        mockMvc.perform(post(SYNC_URL));

        sync()
                .andExpect(jsonPath("$.data.createdCount").value(0))
                .andExpect(jsonPath("$.data.unchangedCount").value(2));
        assertThat(jobPostingRepository.count()).isEqualTo(2);
    }

    @Test
    void 내용이_바뀌면_수정하고_ID는_유지() throws Exception {
        given(seoulJobApiClient.fetchAll()).willReturn(List.of(row("A").build(), row("B").build()));
        mockMvc.perform(post(SYNC_URL));
        Long id = findByTitle("A").getId();

        given(seoulJobApiClient.fetchAll()).willReturn(List.of(
                row("A").jobCont("업무 변경").salTpNm("월급3,000,000원 이상,").build(),
                row("B").build()));

        sync()
                .andExpect(jsonPath("$.data.updatedCount").value(1))
                .andExpect(jsonPath("$.data.unchangedCount").value(1));

        JobPosting updated = findByTitle("A");
        assertThat(updated.getId()).isEqualTo(id);
        assertThat(updated.getDescription()).isEqualTo("업무 변경");
        assertThat(updated.getPays()).singleElement()
                .satisfies(pay -> assertThat(pay.getAmountMin()).isEqualTo(3_000_000L));
    }

    @Test
    void 원본에서_사라진_공고는_마감() throws Exception {
        given(seoulJobApiClient.fetchAll()).willReturn(List.of(row("A").build(), row("B").build()));
        mockMvc.perform(post(SYNC_URL));

        given(seoulJobApiClient.fetchAll()).willReturn(List.of(row("A").build()));

        sync()
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.unchangedCount").value(1))
                .andExpect(jsonPath("$.data.closedCount").value(1));
        assertThat(findByTitle("B").getStatus()).isEqualTo(JobPostingStatus.CLOSE);
        assertThat(findByTitle("A").getStatus()).isEqualTo(JobPostingStatus.OPEN);
    }

    @Test
    void 마감일이_지난_공고는_마감으로_저장하고_모집중이던_공고는_closed로_집계() throws Exception {
        given(seoulJobApiClient.fetchAll()).willReturn(List.of(row("A").build(), row("B").closeDt("20-01-01").build()));
        sync().andExpect(jsonPath("$.data.createdCount").value(2));
        assertThat(findByTitle("B").getStatus()).isEqualTo(JobPostingStatus.CLOSE);

        given(seoulJobApiClient.fetchAll()).willReturn(List.of(row("A").closeDt("20-01-01").build(), row("B").closeDt("20-01-01").build()));
        sync()
                .andExpect(jsonPath("$.data.closedCount").value(1))
                .andExpect(jsonPath("$.data.unchangedCount").value(1));
        assertThat(findByTitle("A").getStatus()).isEqualTo(JobPostingStatus.CLOSE);
    }

    @Test
    void 마감됐던_공고가_다시_나오면_모집중으로_수정() throws Exception {
        given(seoulJobApiClient.fetchAll()).willReturn(List.of(row("A").build(), row("B").build()));
        mockMvc.perform(post(SYNC_URL));
        given(seoulJobApiClient.fetchAll()).willReturn(List.of(row("A").build()));
        mockMvc.perform(post(SYNC_URL));

        given(seoulJobApiClient.fetchAll()).willReturn(List.of(row("A").build(), row("B").build()));

        sync().andExpect(jsonPath("$.data.updatedCount").value(1));
        assertThat(findByTitle("B").getStatus()).isEqualTo(JobPostingStatus.OPEN);
    }

    @Test
    void 원본에_중복_공고가_있으면_한_건만_저장() throws Exception {
        given(seoulJobApiClient.fetchAll()).willReturn(List.of(row("A").build(), row("A").build()));

        sync()
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.createdCount").value(1));
    }

    @Test
    void 외부_API_실패면_502() throws Exception {
        given(seoulJobApiClient.fetchAll())
                .willThrow(new CustomException(JobPostingErrorCode.EXTERNAL_API_FAILED));

        mockMvc.perform(post(SYNC_URL))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("JOB_POSTING_502"));
    }

    @Test
    void 원본이_0건이면_502이고_기존_공고는_유지() throws Exception {
        given(seoulJobApiClient.fetchAll()).willReturn(List.of(row("A").build()));
        mockMvc.perform(post(SYNC_URL));

        given(seoulJobApiClient.fetchAll()).willReturn(List.<SeoulJobRow>of());

        mockMvc.perform(post(SYNC_URL))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("JOB_POSTING_502"));
        assertThat(findByTitle("A").getStatus()).isEqualTo(JobPostingStatus.OPEN);
    }

    private ResultActions sync() throws Exception {
        return mockMvc.perform(post(SYNC_URL)).andExpect(status().isOk());
    }

    private JobPosting findByTitle(String title) {
        return jobPostingRepository.findAll().stream()
                .filter(posting -> posting.getTitle().equals(title))
                .findFirst()
                .orElseThrow();
    }
}
