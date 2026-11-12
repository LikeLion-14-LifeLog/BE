package com.lifelog.domain.jobposting.controller;

import com.lifelog.domain.company.entity.Company;
import com.lifelog.domain.company.repository.CompanyRepository;
import com.lifelog.domain.jobposting.entity.CareerType;
import com.lifelog.domain.jobposting.entity.DeadlineType;
import com.lifelog.domain.jobposting.entity.EmploymentType;
import com.lifelog.domain.jobposting.entity.JobPosting;
import com.lifelog.domain.jobposting.entity.JobPostingStatus;
import com.lifelog.domain.jobposting.repository.JobPostingBookmarkRepository;
import com.lifelog.domain.jobposting.repository.JobPostingRepository;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JobPostingBookmarkControllerTest {

    private static final String URL = "/api/job-postings/{jobPostingId}/bookmark";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobPostingBookmarkRepository jobPostingBookmarkRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Member member;
    private Member other;
    private JobPosting posting;

    @BeforeEach
    void setUp() {
        jobPostingBookmarkRepository.deleteAll();
        jobPostingRepository.deleteAll();
        companyRepository.deleteAll();
        Company company = companyRepository.save(Company.builder().name("회사").build());
        member = memberRepository.save(new Member("bookmark-1@test.com"));
        other = memberRepository.save(new Member("bookmark-2@test.com"));
        posting = jobPostingRepository.saveAndFlush(JobPosting.builder()
                .title("공고")
                .employmentType(EmploymentType.FULL_TIME)
                .careerType(CareerType.ANY)
                .regionCode("11680")
                .workplaceAddress("주소")
                .deadlineType(DeadlineType.ALWAYS)
                .source("TEST")
                .externalId("bookmark-test")
                .company(company)
                .build());
    }

    @Test
    void 저장_응답_형식() throws Exception {
        mockMvc.perform(as(member, post(URL, posting.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.data.jobPostingId").value(posting.getId()))
                .andExpect(jsonPath("$.data.bookmarked").value(true))
                .andExpect(jsonPath("$.data.bookmarkCount").value(1));

        assertThat(jobPostingBookmarkRepository.existsByMember_IdAndJobPosting_Id(member.getId(), posting.getId())).isTrue();
    }

    @Test
    void 이미_저장한_공고를_다시_저장해도_성공하고_저장수는_그대로() throws Exception {
        mockMvc.perform(as(member, post(URL, posting.getId())));

        mockMvc.perform(as(member, post(URL, posting.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(true))
                .andExpect(jsonPath("$.data.bookmarkCount").value(1));
        assertThat(jobPostingBookmarkRepository.countByMember_Id(member.getId())).isEqualTo(1);
    }

    @Test
    void 여러_회원이_저장하면_저장수가_누적() throws Exception {
        mockMvc.perform(as(member, post(URL, posting.getId())));

        mockMvc.perform(as(other, post(URL, posting.getId())))
                .andExpect(jsonPath("$.data.bookmarkCount").value(2));
    }

    @Test
    void 저장_취소() throws Exception {
        mockMvc.perform(as(member, post(URL, posting.getId())));
        mockMvc.perform(as(other, post(URL, posting.getId())));

        mockMvc.perform(as(member, delete(URL, posting.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(false))
                .andExpect(jsonPath("$.data.bookmarkCount").value(1));
        assertThat(jobPostingBookmarkRepository.existsByMember_IdAndJobPosting_Id(member.getId(), posting.getId())).isFalse();
    }

    @Test
    void 저장하지_않은_공고를_취소해도_성공하고_저장수는_그대로() throws Exception {
        mockMvc.perform(as(other, post(URL, posting.getId())));

        mockMvc.perform(as(member, delete(URL, posting.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(false))
                .andExpect(jsonPath("$.data.bookmarkCount").value(1));
    }

    @Test
    void 두번_취소해도_저장수는_한번만_감소() throws Exception {
        mockMvc.perform(as(member, post(URL, posting.getId())));
        mockMvc.perform(as(member, delete(URL, posting.getId())));

        mockMvc.perform(as(member, delete(URL, posting.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarkCount").value(0));
    }

    @Test
    void 마감된_공고도_저장_가능() throws Exception {
        jobPostingRepository.updateStatus("TEST", List.of("bookmark-test"), JobPostingStatus.CLOSE, LocalDateTime.now());

        mockMvc.perform(as(member, post(URL, posting.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(true));
    }

    @Test
    void 없는_공고면_404() throws Exception {
        mockMvc.perform(as(member, post(URL, 999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_POSTING_404"));
        mockMvc.perform(as(member, delete(URL, 999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_POSTING_404"));
    }

    @Test
    void 비로그인이면_401() throws Exception {
        mockMvc.perform(post(URL, posting.getId()).with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401"));
    }

    // JwtAuthenticationFilter와 같이 principal에 회원 ID를 넣음
    private MockHttpServletRequestBuilder as(Member who, MockHttpServletRequestBuilder builder) {
        return builder.with(authentication(new UsernamePasswordAuthenticationToken(
                who.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
    }
}
