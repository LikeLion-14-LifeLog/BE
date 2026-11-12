package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.company.entity.Company;
import com.lifelog.domain.company.repository.CompanyRepository;
import com.lifelog.domain.jobposting.entity.CareerType;
import com.lifelog.domain.jobposting.entity.DeadlineType;
import com.lifelog.domain.jobposting.entity.EmploymentType;
import com.lifelog.domain.jobposting.entity.JobPosting;
import com.lifelog.domain.jobposting.repository.JobPostingBookmarkRepository;
import com.lifelog.domain.jobposting.repository.JobPostingRepository;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 커밋된 상태에서 동시 요청을 보내야 하므로 @Transactional을 붙이지 않고 직접 정리
@SpringBootTest
class JobPostingBookmarkConcurrencyTest {

    private static final int MEMBER_COUNT = 20;

    @Autowired
    private JobPostingBookmarkService jobPostingBookmarkService;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobPostingBookmarkRepository jobPostingBookmarkRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private MemberRepository memberRepository;

    private final List<Member> members = new ArrayList<>();
    private JobPosting posting;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder().name("동시성 테스트 회사").build());
        posting = jobPostingRepository.save(JobPosting.builder()
                .title("동시성 테스트 공고")
                .employmentType(EmploymentType.FULL_TIME)
                .careerType(CareerType.ANY)
                .regionCode("11680")
                .workplaceAddress("주소")
                .deadlineType(DeadlineType.ALWAYS)
                .source("TEST")
                .externalId("bookmark-concurrency")
                .company(company)
                .build());
        for (int i = 0; i < MEMBER_COUNT; i++) {
            members.add(memberRepository.save(new Member("bookmark-concurrency-" + i + "@test.com")));
        }
    }

    @AfterEach
    void tearDown() {
        jobPostingBookmarkRepository.deleteAll();
        jobPostingRepository.deleteAll();
        companyRepository.deleteAll();
        memberRepository.deleteAll(members);
    }

    @Test
    void 여러_회원이_동시에_저장해도_저장수가_누락되지_않음() throws Exception {
        runConcurrently(members.stream()
                .<Callable<Object>>map(member -> () -> jobPostingBookmarkService.bookmark(member.getId(), posting.getId()))
                .toList());

        assertThat(jobPostingRepository.findBookmarkCount(posting.getId())).contains(MEMBER_COUNT);
        assertThat(jobPostingBookmarkRepository.count()).isEqualTo(MEMBER_COUNT);
    }

    @Test
    void 여러_회원이_동시에_저장_취소해도_저장수가_0으로_맞음() throws Exception {
        members.forEach(member -> jobPostingBookmarkService.bookmark(member.getId(), posting.getId()));

        runConcurrently(members.stream()
                .<Callable<Object>>map(member -> () -> jobPostingBookmarkService.unbookmark(member.getId(), posting.getId()))
                .toList());

        assertThat(jobPostingRepository.findBookmarkCount(posting.getId())).contains(0);
        assertThat(jobPostingBookmarkRepository.count()).isZero();
    }

    private void runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            for (Future<Object> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
