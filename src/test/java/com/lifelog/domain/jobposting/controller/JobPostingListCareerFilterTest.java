package com.lifelog.domain.jobposting.controller;

import com.lifelog.domain.company.entity.Company;
import com.lifelog.domain.company.repository.CompanyRepository;
import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.domain.jobposting.entity.CareerType;
import com.lifelog.domain.jobposting.entity.DeadlineType;
import com.lifelog.domain.jobposting.entity.EmploymentType;
import com.lifelog.domain.jobposting.entity.JobPosting;
import com.lifelog.domain.jobposting.repository.JobPostingRepository;
import com.lifelog.domain.jobposting.service.MemberJobProfile;
import com.lifelog.domain.jobposting.service.MemberJobProfileReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

// 경력 조건 설정(job-posting.personalized.new-or-any-only)을 켰을 때
@SpringBootTest(properties = "job-posting.personalized.new-or-any-only=true")
@AutoConfigureMockMvc
@Transactional
class JobPostingListCareerFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @MockitoBean
    private MemberJobProfileReader memberJobProfileReader;

    @Test
    void 설정을_켜면_맞춤_목록은_신입과_경력무관만() throws Exception {
        jobPostingRepository.deleteAll();
        Company company = companyRepository.save(Company.builder().name("회사").build());
        Job job = jobRepository.saveAndFlush(Job.builder()
                .mainCategoryCode("1").mainCategoryName("연구직 및 공학 기술직")
                .middleCategoryCode("13").middleCategoryName("정보통신 연구개발직 및 공학기술직")
                .subCategoryCode("133").subCategoryName("소프트웨어 개발자")
                .code("1332").name("응용 소프트웨어 개발자")
                .build());
        save(company, job, "신입", CareerType.NEW);
        save(company, job, "무관", CareerType.ANY);
        save(company, job, "경력", CareerType.EXPERIENCED);
        given(memberJobProfileReader.read(any())).willReturn(new MemberJobProfile(Set.of(job.getId()), 5));

        mockMvc.perform(get("/api/job-postings").with(authentication(new UsernamePasswordAuthenticationToken(
                        1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(jsonPath("$.data.tabCounts.recommended").value(2))
                .andExpect(jsonPath("$.data.jobPostings[*].title", containsInAnyOrder("신입", "무관")));
    }

    private void save(Company company, Job job, String title, CareerType careerType) {
        JobPosting posting = JobPosting.builder()
                .title(title)
                .employmentType(EmploymentType.FULL_TIME)
                .careerType(careerType)
                .regionCode("11680")
                .workplaceAddress("주소")
                .deadlineType(DeadlineType.ALWAYS)
                .source("TEST")
                .externalId("career-" + title)
                .company(company)
                .build();
        posting.replaceJob(job);
        jobPostingRepository.saveAndFlush(posting);
    }
}
