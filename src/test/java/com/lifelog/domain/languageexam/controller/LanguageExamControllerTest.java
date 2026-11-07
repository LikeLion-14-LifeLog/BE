package com.lifelog.domain.languageexam.controller;

import com.lifelog.domain.languageexam.repository.LanguageExamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LanguageExamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LanguageExamRepository languageExamRepository;

    @BeforeEach
    void setUp() {
        languageExamRepository.deleteAllInBatch();
    }

    @Test
    void import_응답_형식() throws Exception {
        mockMvc.perform(post("/api/admin/language-exams/import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.data.totalCount").value(9))
                .andExpect(jsonPath("$.data.createdCount").value(9))
                .andExpect(jsonPath("$.data.updatedCount").value(0))
                .andExpect(jsonPath("$.data.unchangedCount").value(0));
    }

    @Test
    void 목록_조회_응답_형식() throws Exception {
        mockMvc.perform(post("/api/admin/language-exams/import"));

        mockMvc.perform(get("/api/language-exams")
                        .param("keyword", "toeic")
                        .param("scoreType", "NUMERIC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.languageExams[0].languageExamId").value(1))
                .andExpect(jsonPath("$.data.languageExams[0].name").value("TOEIC"))
                .andExpect(jsonPath("$.data.languageExams[0].languageType").value("ENGLISH"))
                .andExpect(jsonPath("$.data.languageExams[0].scoreType").value("NUMERIC"));
    }

    @Test
    void 잘못된_enum_값이면_400() throws Exception {
        mockMvc.perform(get("/api/language-exams").param("languageType", "FRENCH"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void keyword가_20자를_넘으면_400() throws Exception {
        mockMvc.perform(get("/api/language-exams").param("keyword", "a".repeat(21)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LANGUAGE_EXAM_400"));
    }
}
