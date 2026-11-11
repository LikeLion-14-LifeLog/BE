package com.lifelog.domain.jobposting.controller;

import com.lifelog.domain.company.entity.Company;
import com.lifelog.domain.company.repository.CompanyRepository;
import com.lifelog.domain.jobposting.entity.CareerType;
import com.lifelog.domain.jobposting.entity.DeadlineType;
import com.lifelog.domain.jobposting.entity.EmploymentType;
import com.lifelog.domain.jobposting.entity.JobPosting;
import com.lifelog.domain.jobposting.entity.JobPostingBookmark;
import com.lifelog.domain.jobposting.entity.JobPostingPay;
import com.lifelog.domain.jobposting.entity.JobPostingRequirement;
import com.lifelog.domain.jobposting.entity.JobPostingStatus;
import com.lifelog.domain.jobposting.entity.PayPeriod;
import com.lifelog.domain.jobposting.entity.PayPolicy;
import com.lifelog.domain.jobposting.entity.PayStage;
import com.lifelog.domain.jobposting.entity.RequirementPriority;
import com.lifelog.domain.jobposting.entity.RequirementType;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JobPostingDetailControllerTest {

    private static final String URL = "/api/job-postings/{jobPostingId}";
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
        member = memberRepository.save(new Member("job-posting-detail@test.com"));
    }

    @Test
    void 정상_조회_응답_형식() throws Exception {
        JobPosting posting = fullPosting(TODAY.plusDays(3));
        jobPostingBookmarkRepository.saveAndFlush(new JobPostingBookmark(member, posting));
        setBookmarkCount(posting, 312);

        mockMvc.perform(request(get(URL, posting.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.data.jobPostingId").value(posting.getId()))
                .andExpect(jsonPath("$.data.title").value("주니어 백엔드 웹 개발자"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.company.name").value("(주)네오커머스"))
                .andExpect(jsonPath("$.data.company.industryLabel").value("이커머스"))
                .andExpect(jsonPath("$.data.pay.text").value("연봉 3,600만 ~ 4,000만 원"))
                .andExpect(jsonPath("$.data.pay.amountMin").value(36_000_000))
                .andExpect(jsonPath("$.data.pay.amountMax").value(40_000_000))
                .andExpect(jsonPath("$.data.workRegion").value("서울 강남구"))
                .andExpect(jsonPath("$.data.workplaceAddress").value("서울시 강남구 테헤란로 142"))
                .andExpect(jsonPath("$.data.employmentType").value("FULL_TIME"))
                .andExpect(jsonPath("$.data.employmentTypeLabel").value("정규직"))
                .andExpect(jsonPath("$.data.educationRequirement").value("대졸(4년제) 이상"))
                .andExpect(jsonPath("$.data.closesAt").value(TODAY.plusDays(3).toString()))
                .andExpect(jsonPath("$.data.dDay").value(3))
                .andExpect(jsonPath("$.data.sections[*].title",
                        contains("주요 업무", "지원 요건", "근무 환경 및 혜택", "지원 방법")))
                .andExpect(jsonPath("$.data.sections[0].content").value("백엔드 API 개발"))
                .andExpect(jsonPath("$.data.sections[1].content").value("학력: 대졸(4년제) 이상\n우대 사항: Spring 경험"))
                .andExpect(jsonPath("$.data.sections[2].content").value("주 5일 근무\n4대보험\n퇴직금"))
                .andExpect(jsonPath("$.data.sections[3].content").value("서류,면접\n이력서"))
                .andExpect(jsonPath("$.data.bookmarked").value(true))
                .andExpect(jsonPath("$.data.bookmarkCount").value(312))
                .andExpect(jsonPath("$.data.applyUrl").doesNotExist());
    }

    @Test
    void 마감된_공고도_조회되고_dDay는_음수() throws Exception {
        JobPosting posting = fullPosting(TODAY.minusDays(2));
        jobPostingRepository.updateStatus(posting.getSource(), List.of(posting.getExternalId()),
                JobPostingStatus.CLOSE, LocalDateTime.now());

        mockMvc.perform(request(get(URL, posting.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSE"))
                .andExpect(jsonPath("$.data.dDay").value(-2))
                .andExpect(jsonPath("$.data.bookmarked").value(false));
    }

    @Test
    void 비어_있는_섹션과_없는_급여는_응답에_나오지_않음() throws Exception {
        JobPosting posting = jobPostingRepository.saveAndFlush(base(null)
                .description("업무 내용만 있음")
                .build());

        mockMvc.perform(request(get(URL, posting.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections[*].title", contains("주요 업무")))
                .andExpect(jsonPath("$.data.pay").value(nullValue()))
                .andExpect(jsonPath("$.data.closesAt").value(nullValue()))
                .andExpect(jsonPath("$.data.dDay").value(nullValue()))
                .andExpect(jsonPath("$.data.company.industryLabel").value("이커머스"));
    }

    @Test
    void 회사_내규_급여는_금액이_null() throws Exception {
        JobPosting posting = base(null).build();
        posting.addPay(JobPostingPay.builder()
                .stage(PayStage.CURRENT).payPolicy(PayPolicy.COMPANY_POLICY).payPeriod(PayPeriod.YEARLY)
                .description("회사 내규에 따름").build());
        jobPostingRepository.saveAndFlush(posting);

        mockMvc.perform(request(get(URL, posting.getId())))
                .andExpect(jsonPath("$.data.pay.text").value("회사 내규에 따름"))
                .andExpect(jsonPath("$.data.pay.amountMin").value(nullValue()))
                .andExpect(jsonPath("$.data.pay.amountMax").value(nullValue()));
    }

    @Test
    void 없는_공고면_404() throws Exception {
        mockMvc.perform(request(get(URL, 999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_POSTING_404"));
    }

    @Test
    void 비로그인이면_401() throws Exception {
        mockMvc.perform(get(URL, 1L).with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401"));
    }

    private JobPosting fullPosting(LocalDate closeDate) {
        JobPosting posting = base(closeDate)
                .description("백엔드 API 개발")
                .workSchedule("주 5일 근무")
                .benefits("4대보험\n퇴직금")
                .applicationMethod("서류,면접\n이력서")
                .build();
        posting.addPay(JobPostingPay.builder()
                .stage(PayStage.CURRENT).payPolicy(PayPolicy.SPECIFIED).payPeriod(PayPeriod.YEARLY)
                .amountMin(36_000_000L).amountMax(40_000_000L)
                .description("연봉 3,600만 ~ 4,000만 원").build());
        posting.addRequirement(JobPostingRequirement.builder()
                .priority(RequirementPriority.REQUIRED).type(RequirementType.DEGREE)
                .content("대졸(4년제) 이상").sortOrder((short) 1).build());
        posting.addRequirement(JobPostingRequirement.builder()
                .priority(RequirementPriority.PREFERRED).type(RequirementType.ETC)
                .content("Spring 경험").sortOrder((short) 2).build());
        return jobPostingRepository.saveAndFlush(posting);
    }

    private JobPosting.JobPostingBuilder base(LocalDate closeDate) {
        return JobPosting.builder()
                .title("주니어 백엔드 웹 개발자")
                .employmentType(EmploymentType.FULL_TIME)
                .careerType(CareerType.NEW)
                .regionCode("11680")
                .regionName("서울 강남구")
                .workplaceAddress("서울시 강남구 테헤란로 142")
                .deadlineType(closeDate == null ? DeadlineType.ALWAYS : DeadlineType.FIXED)
                .closesAt(closeDate == null ? null : closeDate.atTime(23, 59, 59))
                .source("TEST")
                .externalId("detail-" + System.nanoTime())
                .educationRequirement("대졸(4년제) 이상")
                .company(company);
    }

    // bookmark_count는 엔티티로 쓰지 않으므로 쿼리로 설정
    private void setBookmarkCount(JobPosting posting, int count) {
        entityManager.createQuery("UPDATE JobPosting p SET p.bookmarkCount = :count WHERE p.id = :id")
                .setParameter("count", count)
                .setParameter("id", posting.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private MockHttpServletRequestBuilder request(MockHttpServletRequestBuilder builder) {
        return builder.with(authentication(new UsernamePasswordAuthenticationToken(
                member.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")))));
    }
}
