
package com.lifelog.domain.job.controller;

import com.lifelog.domain.job.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "testuser", roles = "USER")
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAllInBatch();
    }

    @Test
    void init_응답_형식() throws Exception {
        mockMvc.perform(post("/api/admin/jobs/init"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.data.totalCount").value(451))
                .andExpect(jsonPath("$.data.createdCount").value(451));
    }

    @Test
    void 목록_조회_응답_형식() throws Exception {
        mockMvc.perform(post("/api/admin/jobs/init"));

        mockMvc.perform(get("/api/jobs").param("code", "0111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs[0].jobId").isNumber())
                .andExpect(jsonPath("$.data.jobs[0].mainCategory.code").value("0"))
                .andExpect(jsonPath("$.data.jobs[0].mainCategory.name").value("경영·사무·금융·보험직"))
                .andExpect(jsonPath("$.data.jobs[0].middleCategory.code").value("01"))
                .andExpect(jsonPath("$.data.jobs[0].subCategory.code").value("011"))
                .andExpect(jsonPath("$.data.jobs[0].code").value("0111"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 미분류_9999_응답은_대분류코드와_중소분류가_null() throws Exception {
        mockMvc.perform(post("/api/admin/jobs/init"));

        mockMvc.perform(get("/api/jobs").param("code", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs[0].mainCategory.code").value(nullValue()))
                .andExpect(jsonPath("$.data.jobs[0].mainCategory.name").value("미분류"))
                .andExpect(jsonPath("$.data.jobs[0].middleCategory").value(nullValue()))
                .andExpect(jsonPath("$.data.jobs[0].subCategory").value(nullValue()))
                .andExpect(jsonPath("$.data.jobs[0].code").value("9999"))
                .andExpect(jsonPath("$.data.jobs[0].name").value("미분류"));
    }

    @Test
    void 대분류_9_필터에_미분류_9999는_나오지_않음() throws Exception {
        mockMvc.perform(post("/api/admin/jobs/init"));

        mockMvc.perform(get("/api/jobs")
                        .param("mainCategoryCode", "9")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs[*].code", hasItem("9050")))
                .andExpect(jsonPath("$.data.jobs[*].code", not(hasItem("9999"))));
    }

    @Test
    void page_0_size_101이면_400() throws Exception {
        mockMvc.perform(get("/api/jobs")
                        .param("page", "0")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void page_0이면_400() throws Exception {
        mockMvc.perform(get("/api/jobs").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JOB_400_2"));
    }

    @Test
    void size_101이면_400() throws Exception {
        mockMvc.perform(get("/api/jobs").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JOB_400_3"));
    }

    @Test
    void keyword가_50자를_넘으면_400() throws Exception {
        mockMvc.perform(get("/api/jobs")
                        .param("keyword", "가".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JOB_400_1"));
    }
}
