package com.lifelog.domain.job.service;

import com.lifelog.domain.job.dto.JobInitResponse;
import com.lifelog.domain.job.dto.JobListResponse;
import com.lifelog.domain.job.dto.JobResponse;
import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.repository.JobRepository;
import com.lifelog.global.response.SliceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JobServiceTest {

    private static final int CSV_ROW_COUNT = 451;

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @BeforeEach
    void setUp() {
        // 로컬 DB에 이미 데이터가 있어도 테스트가 독립적으로 돌도록 비움 (트랜잭션 롤백됨)
        jobRepository.deleteAllInBatch();
    }

    @Test
    void 처음_init하면_전부_created() {
        JobInitResponse response = jobService.initFromCsv();

        assertThat(response.totalCount()).isEqualTo(CSV_ROW_COUNT);
        assertThat(response.createdCount()).isEqualTo(CSV_ROW_COUNT);
        assertThat(response.updatedCount()).isZero();
        assertThat(response.unchangedCount()).isZero();
    }

    @Test
    void 두번_init해도_전부_unchanged이고_jobId_유지() {
        jobService.initFromCsv();
        Map<String, Long> firstIds = findIdsByCode();

        JobInitResponse second = jobService.initFromCsv();

        assertThat(second.createdCount()).isZero();
        assertThat(second.updatedCount()).isZero();
        assertThat(second.unchangedCount()).isEqualTo(CSV_ROW_COUNT);
        assertThat(jobRepository.count()).isEqualTo(CSV_ROW_COUNT);
        assertThat(findIdsByCode()).isEqualTo(firstIds);
    }

    @Test
    void 값이_다르면_updated이고_jobId_유지() {
        jobService.initFromCsv();
        Job job = findByCode("0111");
        Long jobId = job.getId();
        job.update(Job.builder()
                .mainCategoryCode("0").mainCategoryName("변경")
                .middleCategoryCode("01").middleCategoryName("변경")
                .subCategoryCode("011").subCategoryName("변경")
                .code("0111").name("변경된 이름")
                .build());
        jobRepository.flush();

        JobInitResponse response = jobService.initFromCsv();

        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(response.unchangedCount()).isEqualTo(CSV_ROW_COUNT - 1);
        Job updated = findByCode("0111");
        assertThat(updated.getId()).isEqualTo(jobId);
        assertThat(updated.getName()).isEqualTo("의회의원·고위공무원 및 공공단체임원");
    }

    @Test
    void CSV에_없는_기존_직무는_삭제하지_않음() {
        jobRepository.saveAndFlush(Job.builder()
                .mainCategoryCode("9").mainCategoryName("기타")
                .middleCategoryCode("99").middleCategoryName("기타")
                .subCategoryCode("999").subCategoryName("기타")
                .code("9998").name("CSV 밖 직무")
                .build());

        jobService.initFromCsv();

        assertThat(jobRepository.count()).isEqualTo(CSV_ROW_COUNT + 1);
    }

    @Test
    void keyword_임원_검색() {
        jobService.initFromCsv();

        SliceResponse<JobListResponse> response = jobService.search("임원", null, null, null, null, 1, 20);

        assertThat(response.content().jobs()).extracting(JobResponse::code).contains("0111", "0112");
    }

    @Test
    void keyword_앞뒤공백_제거() {
        jobService.initFromCsv();

        SliceResponse<JobListResponse> trimmed =jobService.search("  임원 ", null, null, null, null, 1, 20);
        SliceResponse<JobListResponse> plain =jobService.search("임원", null, null, null, null, 1, 20);

        assertThat(trimmed.totalElements()).isEqualTo(plain.totalElements());
    }

    @Test
    void code로_정확히_1건() {
        jobService.initFromCsv();

        SliceResponse<JobListResponse> response = jobService.search(null, "0111", null, null, null, 1, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content().jobs().get(0).code()).isEqualTo("0111");
        assertThat(response.content().jobs().get(0).mainCategory().code()).isEqualTo("0");
        assertThat(response.content().jobs().get(0).subCategory().code()).isEqualTo("011");
    }

    @Test
    void 분류_조건은_AND로_적용() {
        jobService.initFromCsv();

        SliceResponse<JobListResponse> response = jobService.search("관리자", null, "0", "01", "013", 1, 100);

        assertThat(response.content().jobs()).isNotEmpty()
                .allSatisfy(job -> assertThat(job.subCategory().code()).isEqualTo("013"));
    }

    @Test
    void 조건_없으면_code_오름차순_페이지네이션() {
        jobService.initFromCsv();

        SliceResponse<JobListResponse> first =jobService.search(null, null, null, null, null, 1, 20);
        SliceResponse<JobListResponse> last =jobService.search(null, null, null, null, null, 23, 20);

        assertThat(first.totalElements()).isEqualTo(CSV_ROW_COUNT);
        assertThat(first.totalPages()).isEqualTo(23);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.content().jobs()).hasSize(20);
        assertThat(first.content().jobs()).extracting(JobResponse::code).isSorted();
        assertThat(last.content().jobs()).hasSize(11);
        assertThat(last.hasNext()).isFalse();
    }

    @Test
    void 결과가_없으면_totalPages_0() {
        jobService.initFromCsv();

        SliceResponse<JobListResponse> response = jobService.search("존재하지않는직무", null, null, null, null, 1, 20);

        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void 미분류_9999는_대분류코드와_중소분류가_null로_저장() {
        jobService.initFromCsv();

        Job job = findByCode("9999");

        assertThat(job.getMainCategoryCode()).isNull();
        assertThat(job.getMainCategoryName()).isEqualTo("미분류");
        assertThat(job.getMiddleCategoryCode()).isNull();
        assertThat(job.getMiddleCategoryName()).isNull();
        assertThat(job.getSubCategoryCode()).isNull();
        assertThat(job.getSubCategoryName()).isNull();
        assertThat(job.getName()).isEqualTo("미분류");
    }

    @Test
    void 대분류_9_필터에_미분류_9999는_나오지_않음() {
        jobService.initFromCsv();

        SliceResponse<JobListResponse> response = jobService.search(null, null, "9", null, null, 1, 100);

        assertThat(response.content().jobs()).isNotEmpty()
                .extracting(JobResponse::code)
                .contains("9050")
                .doesNotContain("9999");
    }

    @Test
    void 기존_9_99_999로_저장된_미분류는_init하면_null로_갱신() {
        jobRepository.saveAndFlush(Job.builder()
                .mainCategoryCode("9").mainCategoryName("미분류")
                .middleCategoryCode("99").middleCategoryName("미분류")
                .subCategoryCode("999").subCategoryName("미분류")
                .code("9999").name("미분류")
                .build());

        JobInitResponse response = jobService.initFromCsv();
        jobRepository.flush();

        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(response.createdCount()).isEqualTo(CSV_ROW_COUNT - 1);
        assertThat(findByCode("9999").getMainCategoryCode()).isNull();
        assertThat(findByCode("9999").getSubCategoryCode()).isNull();
    }

    private Map<String, Long> findIdsByCode() {
        return jobRepository.findAll().stream()
                .collect(Collectors.toMap(Job::getCode, Job::getId));
    }

    private Job findByCode(String code) {
        return jobRepository.findAll().stream()
                .collect(Collectors.toMap(Job::getCode, Function.identity()))
                .get(code);
    }
}
