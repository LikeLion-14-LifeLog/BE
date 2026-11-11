package com.lifelog.domain.jobposting.controller;

import com.lifelog.domain.company.entity.Company;
import com.lifelog.domain.company.repository.CompanyRepository;
import com.lifelog.domain.jobposting.entity.CareerType;
import com.lifelog.domain.jobposting.entity.DeadlineType;
import com.lifelog.domain.jobposting.entity.EmploymentType;
import com.lifelog.domain.jobposting.entity.JobGroup;
import com.lifelog.domain.jobposting.entity.JobPosting;
import com.lifelog.domain.jobposting.entity.JobPostingBookmark;
import com.lifelog.domain.jobposting.entity.JobPostingStatus;
import com.lifelog.domain.jobposting.repository.JobPostingBookmarkRepository;
import com.lifelog.domain.jobposting.repository.JobPostingRepository;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JobPostingListControllerTest {

    private static final String URL = "/api/job-postings";
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

    @Autowired
    private EntityManager entityManager;

    private Company company;
    private Member member;

    @BeforeEach
    void setUp() {
        jobPostingBookmarkRepository.deleteAll();
        jobPostingRepository.deleteAll();
        companyRepository.deleteAll();
        company = companyRepository.save(Company.builder().name("(주)네오커머스").industryName("이커머스").build());
        member = memberRepository.save(new Member("job-posting-list@test.com"));
    }

    @Test
    void 응답_형식() throws Exception {
        JobPosting posting = save("주니어 백엔드 개발자", "11680", "서울 강남구", JobGroup.BACKEND, TODAY.plusDays(3));
        save("다른 공고", "11680", "서울 강남구", null, null);
        bookmark(posting);

        mockMvc.perform(request(get(URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.data.tabCounts.recommended").value(2))
                .andExpect(jsonPath("$.data.tabCounts.bookmarked").value(1))
                .andExpect(jsonPath("$.data.tabCounts.offers").doesNotExist())
                .andExpect(jsonPath("$.data.jobPostings[0].jobPostingId").value(posting.getId()))
                .andExpect(jsonPath("$.data.jobPostings[0].title").value("주니어 백엔드 개발자"))
                .andExpect(jsonPath("$.data.jobPostings[0].companyName").value("(주)네오커머스"))
                .andExpect(jsonPath("$.data.jobPostings[0].companySizeLabel").value(nullValue()))
                .andExpect(jsonPath("$.data.jobPostings[0].industryLabel").value("이커머스"))
                .andExpect(jsonPath("$.data.jobPostings[0].educationRequirement").value("대졸(4년)"))
                .andExpect(jsonPath("$.data.jobPostings[0].workRegion").value("서울 강남구"))
                .andExpect(jsonPath("$.data.jobPostings[0].expectedSalaryMin").value(nullValue()))
                .andExpect(jsonPath("$.data.jobPostings[0].expectedSalaryMax").value(nullValue()))
                .andExpect(jsonPath("$.data.jobPostings[0].dDay").value(3))
                .andExpect(jsonPath("$.data.jobPostings[0].bookmarkCount").value(1))
                .andExpect(jsonPath("$.data.jobPostings[0].bookmarked").value(true))
                .andExpect(jsonPath("$.data.jobPostings[1].dDay").value(nullValue()))
                .andExpect(jsonPath("$.data.jobPostings[1].bookmarked").value(false))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(6))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 마감된_공고는_제외하고_recommended는_필터와_무관() throws Exception {
        save("서울", "11680", "서울 강남구", JobGroup.BACKEND, null);
        save("경기", "41135", "경기 성남시 분당구", JobGroup.FRONTEND, null);
        JobPosting closed = save("마감", "11680", "서울 강남구", JobGroup.BACKEND, null);
        setStatusClosed(closed);

        mockMvc.perform(request(get(URL).param("region", "SEOUL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tabCounts.recommended").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.jobPostings[*].title", contains("서울")));
    }

    @Test
    void 마감_임박순은_마감일_오름차순_상시는_마지막_같으면_최신_ID_먼저() throws Exception {
        JobPosting always = save("상시", "11680", null, null, null);
        JobPosting later = save("나중", "11680", null, null, TODAY.plusDays(10));
        JobPosting soonOld = save("임박-먼저등록", "11680", null, null, TODAY.plusDays(1));
        JobPosting soonNew = save("임박-나중등록", "11680", null, null, TODAY.plusDays(1));

        mockMvc.perform(request(get(URL)))
                .andExpect(jsonPath("$.data.jobPostings[*].jobPostingId", contains(
                        soonNew.getId().intValue(), soonOld.getId().intValue(),
                        later.getId().intValue(), always.getId().intValue())));
    }

    @Test
    void 인기순은_저장수_내림차순_같으면_최신_ID_먼저() throws Exception {
        JobPosting low = save("적음", "11680", null, null, null);
        JobPosting highOld = save("많음-먼저", "11680", null, null, null);
        JobPosting highNew = save("많음-나중", "11680", null, null, null);
        setBookmarkCount(low, 1);
        setBookmarkCount(highOld, 5);
        setBookmarkCount(highNew, 5);

        mockMvc.perform(request(get(URL).param("sort", "POPULAR")))
                .andExpect(jsonPath("$.data.jobPostings[*].jobPostingId", contains(
                        highNew.getId().intValue(), highOld.getId().intValue(), low.getId().intValue())))
                .andExpect(jsonPath("$.data.jobPostings[0].bookmarkCount").value(5));
    }

    @Test
    void 직무_그룹을_지정하면_그_그룹만_없으면_그룹_없는_공고까지_전체() throws Exception {
        save("백엔드", "11680", null, JobGroup.BACKEND, null);
        save("AI", "11680", null, JobGroup.AI_ML, null);
        save("미분류", "11680", null, null, null);

        mockMvc.perform(request(get(URL).param("jobGroup", "BACKEND")))
                .andExpect(jsonPath("$.data.jobPostings[*].title", contains("백엔드")));
        mockMvc.perform(request(get(URL)))
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    void 지역_필터() throws Exception {
        save("강남", "11680", null, null, null);
        save("분당", "41135", null, null, null);
        save("수원", "41117", null, null, null);
        save("인천", "28200", null, null, null);
        save("부산", "26350", null, null, null);

        expectTitles("SEOUL", "강남");
        expectTitles("SEOUL_PANGYO", "강남", "분당");
        expectTitles("GYEONGGI", "분당", "수원");
        expectTitles("INCHEON", "인천");
        expectTitles("BUSAN", "부산");
    }

    @Test
    void 페이지_크기_기본값_6과_다음_페이지() throws Exception {
        for (int i = 0; i < 7; i++) {
            save("공고" + i, "11680", null, null, null);
        }

        mockMvc.perform(request(get(URL)))
                .andExpect(jsonPath("$.data.jobPostings.length()").value(6))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mockMvc.perform(request(get(URL).param("page", "2")))
                .andExpect(jsonPath("$.data.jobPostings.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void page_0이면_400() throws Exception {
        mockMvc.perform(request(get(URL).param("page", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_1"));
    }

    @Test
    void size_101이면_400() throws Exception {
        mockMvc.perform(request(get(URL).param("size", "101")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_2"));
    }

    @Test
    void 잘못된_enum_값이면_400() throws Exception {
        mockMvc.perform(request(get(URL).param("region", "JEJU")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void 비로그인이면_401() throws Exception {
        mockMvc.perform(get(URL).with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401"));
    }

    private void expectTitles(String region, String... titles) throws Exception {
        mockMvc.perform(request(get(URL).param("region", region)))
                .andExpect(jsonPath("$.data.jobPostings[*].title", containsInAnyOrder(titles)));
    }

    // JwtAuthenticationFilter와 같이 principal에 회원 ID를 넣음
    private MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder builder) {
        return builder.with(authentication(new UsernamePasswordAuthenticationToken(
                member.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
    }

    private JobPosting save(String title, String regionCode, String regionName, JobGroup jobGroup, LocalDate closeDate) {
        JobPosting posting = JobPosting.builder()
                .title(title)
                .employmentType(EmploymentType.FULL_TIME)
                .careerType(CareerType.ANY)
                .regionCode(regionCode)
                .regionName(regionName)
                .workplaceAddress("주소")
                .deadlineType(closeDate == null ? DeadlineType.ALWAYS : DeadlineType.FIXED)
                .closesAt(closeDate == null ? null : closeDate.atTime(23, 59, 59))
                .publishedAt(LocalDateTime.now())
                .source("TEST")
                .externalId(title + regionCode)
                .jobGroup(jobGroup)
                .educationRequirement("대졸(4년)")
                .company(company)
                .build();
        return jobPostingRepository.saveAndFlush(posting);
    }

    private void bookmark(JobPosting posting) {
        jobPostingBookmarkRepository.saveAndFlush(new JobPostingBookmark(member, posting));
        setBookmarkCount(posting, 1);
    }

    // bookmark_count는 엔티티로 쓰지 않으므로 쿼리로 설정
    private void setBookmarkCount(JobPosting posting, int count) {
        entityManager.createQuery("UPDATE JobPosting p SET p.bookmarkCount = :count WHERE p.id = :id")
                .setParameter("count", count)
                .setParameter("id", posting.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private void setStatusClosed(JobPosting posting) {
        jobPostingRepository.updateStatus(posting.getSource(), List.of(posting.getExternalId()),
                JobPostingStatus.CLOSE, LocalDateTime.now());
    }
}
