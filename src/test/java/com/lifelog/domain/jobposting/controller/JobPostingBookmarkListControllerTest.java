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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JobPostingBookmarkListControllerTest {

    private static final String URL = "/api/job-postings/bookmarks";
    private static final String BOOKMARK_URL = "/api/job-postings/{jobPostingId}/bookmark";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

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

    private Company company;
    private Member member;
    private Member other;

    @BeforeEach
    void setUp() {
        jobPostingBookmarkRepository.deleteAll();
        jobPostingRepository.deleteAll();
        companyRepository.deleteAll();
        company = companyRepository.save(Company.builder().name("(주)네오커머스").industryName("이커머스").build());
        member = memberRepository.save(new Member("bookmark-list-1@test.com"));
        other = memberRepository.save(new Member("bookmark-list-2@test.com"));
    }

    @Test
    void 응답_형식은_맞춤_목록에서_tabCounts만_빠짐() throws Exception {
        JobPosting posting = save("공고", TODAY.plusDays(3));
        bookmark(member, posting);

        mockMvc.perform(as(member, get(URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.data.tabCounts").doesNotExist())
                .andExpect(jsonPath("$.data.jobPostings[0].jobPostingId").value(posting.getId()))
                .andExpect(jsonPath("$.data.jobPostings[0].title").value("공고"))
                .andExpect(jsonPath("$.data.jobPostings[0].companyName").value("(주)네오커머스"))
                .andExpect(jsonPath("$.data.jobPostings[0].industryLabel").value("이커머스"))
                .andExpect(jsonPath("$.data.jobPostings[0].workRegion").value("서울 강남구"))
                .andExpect(jsonPath("$.data.jobPostings[0].dDay").value(3))
                .andExpect(jsonPath("$.data.jobPostings[0].bookmarkCount").value(1))
                .andExpect(jsonPath("$.data.jobPostings[0].bookmarked").value(true))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(6))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 최근_저장한_공고가_먼저() throws Exception {
        JobPosting first = save("먼저 저장", null);
        JobPosting second = save("나중 저장", null);
        JobPosting third = save("가장 나중 저장", null);
        bookmark(member, second);
        bookmark(member, first);
        bookmark(member, third);

        mockMvc.perform(as(member, get(URL)))
                .andExpect(jsonPath("$.data.jobPostings[*].title", contains("가장 나중 저장", "먼저 저장", "나중 저장")));
    }

    @Test
    void 내가_저장한_공고만_보이고_취소한_공고는_빠짐() throws Exception {
        JobPosting mine = save("내 공고", null);
        JobPosting cancelled = save("취소한 공고", null);
        JobPosting others = save("다른 회원 공고", null);
        bookmark(member, mine);
        bookmark(member, cancelled);
        bookmark(other, others);
        mockMvc.perform(as(member, delete(BOOKMARK_URL, cancelled.getId())));

        mockMvc.perform(as(member, get(URL)))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.jobPostings[*].title", contains("내 공고")));
    }

    @Test
    void 마감된_공고와_마감일_지난_공고도_포함() throws Exception {
        JobPosting closed = save("마감", null);
        JobPosting expired = save("마감일 지남", TODAY.minusDays(1));
        bookmark(member, closed);
        bookmark(member, expired);
        jobPostingRepository.updateStatus("TEST", List.of(closed.getExternalId()), JobPostingStatus.CLOSE, LocalDateTime.now());

        mockMvc.perform(as(member, get(URL)))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.jobPostings[*].title", contains("마감일 지남", "마감")))
                .andExpect(jsonPath("$.data.jobPostings[*].status", contains("OPEN", "CLOSE")))
                .andExpect(jsonPath("$.data.jobPostings[0].dDay").value(-1));
    }

    @Test
    void 저장한_공고가_없으면_빈_목록() throws Exception {
        mockMvc.perform(as(member, get(URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobPostings").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 페이지네이션() throws Exception {
        for (int i = 0; i < 7; i++) {
            bookmark(member, save("공고" + i, null));
        }

        mockMvc.perform(as(member, get(URL).param("page", "2")))
                .andExpect(jsonPath("$.data.jobPostings.length()").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void page_0이면_400() throws Exception {
        mockMvc.perform(as(member, get(URL).param("page", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_1"));
    }

    @Test
    void size_101이면_400() throws Exception {
        mockMvc.perform(as(member, get(URL).param("size", "101")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_2"));
    }

    @Test
    void 비로그인이면_401() throws Exception {
        mockMvc.perform(get(URL).with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401"));
    }

    private void bookmark(Member who, JobPosting posting) throws Exception {
        mockMvc.perform(as(who, post(BOOKMARK_URL, posting.getId()))).andExpect(status().isOk());
    }

    private JobPosting save(String title, LocalDate closeDate) {
        return jobPostingRepository.saveAndFlush(JobPosting.builder()
                .title(title)
                .employmentType(EmploymentType.FULL_TIME)
                .careerType(CareerType.ANY)
                .regionCode("11680")
                .regionName("서울 강남구")
                .workplaceAddress("주소")
                .deadlineType(closeDate == null ? DeadlineType.ALWAYS : DeadlineType.FIXED)
                .closesAt(closeDate == null ? null : closeDate.atTime(23, 59, 59))
                .source("TEST")
                .externalId("bookmark-list-" + title)
                .company(company)
                .build());
    }

    // JwtAuthenticationFilter와 같이 principal에 회원 ID를 넣음
    private MockHttpServletRequestBuilder as(Member who, MockHttpServletRequestBuilder builder) {
        return builder.with(authentication(new UsernamePasswordAuthenticationToken(
                who.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
    }
}
