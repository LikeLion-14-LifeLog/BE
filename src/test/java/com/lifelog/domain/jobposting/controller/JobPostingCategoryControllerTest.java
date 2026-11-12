package com.lifelog.domain.jobposting.controller;

import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.job.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "testuser", roles = "USER")
class JobPostingCategoryControllerTest {

    private static final String URL = "/api/job-postings/job-categories";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobService jobService;

    @BeforeEach
    void setUp() {
        // 직무 기준 데이터(job_2025.csv) 등록 (트랜잭션 롤백됨)
        jobRepository.deleteAllInBatch();
        jobService.initFromCsv();
    }

    @Test
    void 응답_형식() throws Exception {
        mockMvc.perform(get(URL).param("keyword", "소프트웨어"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.data.jobCategories[0].code").value("133"))
                .andExpect(jsonPath("$.data.jobCategories[0].name").value("소프트웨어 개발자"))
                .andExpect(jsonPath("$.data.jobCategories[0].mainCategory.code").value("1"))
                .andExpect(jsonPath("$.data.jobCategories[0].middleCategory.code").value("13"))
                .andExpect(jsonPath("$.data.jobCategories[0].middleCategory.name").value("정보통신 연구개발직 및 공학기술직"))
                .andExpect(jsonPath("$.data.jobCategories[0].jobs[*].code", contains("1331", "1332", "1333", "1339")))
                .andExpect(jsonPath("$.data.jobCategories[0].jobs[0].name").value("시스템 소프트웨어 개발자"));
    }

    @Test
    void 검색어가_없으면_미분류를_뺀_소분류_전체() throws Exception {
        // 한국고용직업분류 2025 소분류 140개, 미분류(9999)는 소분류가 없어 제외
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobCategories.length()").value(140))
                .andExpect(jsonPath("$.data.jobCategories[0].code").value("011"))
                .andExpect(jsonPath("$.data.jobCategories[*].code", not(hasItem((String) null))));
    }

    @Test
    void 소분류명이나_세분류명에_검색어가_있으면_해당_소분류() throws Exception {
        // "소프트웨어 개발자"(133)는 세분류 "웹 개발자"로, "정보시스템 및 웹 운영자"(136)는 소분류명으로 걸림
        mockMvc.perform(get(URL).param("keyword", " 웹 "))
                .andExpect(jsonPath("$.data.jobCategories[*].code", hasItems("133", "136")));
        mockMvc.perform(get(URL).param("keyword", "존재하지않는직무"))
                .andExpect(jsonPath("$.data.jobCategories").isEmpty());
    }

    @Test
    void 검색어가_50자를_넘으면_400() throws Exception {
        mockMvc.perform(get(URL).param("keyword", "가".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JOB_POSTING_400"));
    }

    @Test
    void 비로그인이면_401() throws Exception {
        mockMvc.perform(get(URL).with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401"));
    }
}
