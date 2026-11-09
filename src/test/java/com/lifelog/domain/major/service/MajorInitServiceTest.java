package com.lifelog.domain.major.service;

import com.lifelog.domain.major.dto.MajorCsvReadResult;
import com.lifelog.domain.major.dto.MajorCsvRow;
import com.lifelog.domain.major.dto.MajorInitResDto;
import com.lifelog.domain.major.entity.Major;
import com.lifelog.domain.major.enums.DegreeProcess;
import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.domain.major.enums.MajorMiddleField;
import com.lifelog.domain.major.enums.MajorSubField;
import com.lifelog.domain.major.mapper.MajorMapper;
import com.lifelog.domain.major.repository.MajorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest(classes = MajorInitServiceTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MajorInitServiceTest {

    @Autowired private MajorService majorService;
    @Autowired private MajorRepository majorRepository;
    @Autowired private DataSource dataSource;
    @MockitoBean private MajorCsvReader majorCsvReader;

    @BeforeAll
    void prepareSharedDatabase() throws Exception {
        MajorTestDatabaseSupport.initialize(dataSource);
    }

    @BeforeEach
    void setUp() {
        given(majorCsvReader.read()).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return source();
        });
    }

    @AfterEach
    void cleanUp() {
        majorRepository.deleteAllInBatch();
        assertThat(majorRepository.count()).isZero();
    }

    @Test
    @DisplayName("파싱은 트랜잭션 밖에서 수행하고 고유 전공을 최초 등록한다")
    void 최초_등록_성공() {
        // given / when
        MajorInitResDto result = majorService.initFromCsv();
        // then
        assertThat(result).isEqualTo(MajorInitResDto.create(4, 2, 2, 2, 0, 0));
        assertThat(majorRepository.count()).isEqualTo(2);
        assertThat(majorRepository.findAll()).allSatisfy(major -> assertThat(major.getId()).isNotNull());
    }

    @Test
    @DisplayName("재호출은 모두 unchanged이고 ID를 유지한다")
    void 재호출_전부_unchanged_성공() {
        // given
        majorService.initFromCsv();
        Map<String, Long> ids = idsByCode();
        // when
        MajorInitResDto result = majorService.initFromCsv();
        // then
        assertThat(result).isEqualTo(MajorInitResDto.create(4, 2, 2, 0, 0, 2));
        assertThat(idsByCode()).isEqualTo(ids);
    }

    @Test
    @DisplayName("변경된 전공만 갱신하고 기존 ID를 유지한다")
    void 변경_건만_updated_성공() {
        // given
        majorService.initFromCsv();
        Map<String, Long> ids = idsByCode();
        Major changed = majorRepository.findAll().stream().filter(m -> m.getCode().equals("C01")).findFirst().orElseThrow();
        changed.update(Major.builder().code("C01").name("변경된 학과")
                .degreeProcess(DegreeProcess.UNIVERSITY).mainField(MajorMainField.ENGINEERING)
                .middleField(MajorMiddleField.COMPUTER_COMMUNICATIONS)
                .subField(MajorSubField.COMPUTER_SCIENCE_ENGINEERING).build());
        majorRepository.saveAndFlush(changed);
        // when
        MajorInitResDto result = majorService.initFromCsv();
        // then
        assertThat(result).isEqualTo(MajorInitResDto.create(4, 2, 2, 0, 1, 1));
        assertThat(idsByCode()).isEqualTo(ids);
        Major restored = majorRepository.findById(ids.get("C01")).orElseThrow();
        assertThat(restored.getName()).isEqualTo("일본어과");
        assertThat(restored.getMainField()).isEqualTo(MajorMainField.HUMANITIES);
        assertThat(restored.getMiddleField()).isEqualTo(MajorMiddleField.LANGUAGES_LITERATURE);
        assertThat(restored.getSubField()).isEqualTo(MajorSubField.JAPANESE_LANGUAGE);
        assertThat(restored.getDegreeProcess()).isEqualTo(DegreeProcess.COLLEGE);
    }

    @Test
    @DisplayName("실제 DB 저장 실패 시 앞선 INSERT와 기존 전공 변경도 모두 롤백한다")
    void 저장_실패_전체_롤백_성공() {
        // given
        majorService.initFromCsv();
        Map<String, Long> ids = idsByCode();
        MajorCsvReadResult invalid = new MajorCsvReadResult(3, 0, List.of(
                row("C01", "변경 예정 학과"), row("C03", "신규 학과"), row("C04", "가".repeat(101))));
        // 정상 Reader는 거부하는 길이 초과를 IO 경계 대역에서 주입해 DB 실패를 유발한다.
        given(majorCsvReader.read()).willReturn(invalid);
        // when / then: 테스트 자체에 트랜잭션을 걸지 않아 서비스 프록시의 실제 롤백을 확인한다.
        assertThatThrownBy(() -> majorService.initFromCsv()).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(idsByCode()).isEqualTo(ids);
        assertThat(majorRepository.findById(ids.get("C01")).orElseThrow().getName()).isEqualTo("일본어과");
        // 실패한 트랜잭션의 잠금이 해제되어 다음 초기화도 정상 완료한다.
        given(majorCsvReader.read()).willReturn(source());
        assertThat(majorService.initFromCsv().unchangedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("독립 스레드의 두 트랜잭션이 동시에 초기화해도 중복이 없다")
    void 독립_트랜잭션_동시_초기화_중복없음_성공() throws Exception {
        // given
        CyclicBarrier parsed = new CyclicBarrier(2);
        given(majorCsvReader.read()).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            parsed.await(5, TimeUnit.SECONDS);
            return source();
        });
        // when
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(majorService::initFromCsv);
            var second = executor.submit(majorService::initFromCsv);
            MajorInitResDto a = first.get(20, TimeUnit.SECONDS);
            MajorInitResDto b = second.get(20, TimeUnit.SECONDS);
            // then
            assertThat(a.createdCount() + b.createdCount()).isEqualTo(2);
            assertThat(a.unchangedCount() + b.unchangedCount()).isEqualTo(2);
            assertThat(a.updatedCount() + b.updatedCount()).isZero();
            assertThat(majorRepository.count()).isEqualTo(2);
            assertThat(idsByCode()).hasSize(2);
        }
    }

    private Map<String, Long> idsByCode() {
        return majorRepository.findAll().stream().collect(Collectors.toMap(Major::getCode, Major::getId));
    }

    private MajorCsvReadResult source() {
        return new MajorCsvReadResult(4, 2, List.of(row("C01", "일본어과"), row("C02", "일본어통역과")));
    }

    private MajorCsvRow row(String code, String name) {
        return new MajorCsvRow(DegreeProcess.COLLEGE, MajorMainField.HUMANITIES,
                MajorMiddleField.LANGUAGES_LITERATURE, MajorSubField.JAPANESE_LANGUAGE, name, code);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Major.class)
    @EnableJpaRepositories(basePackageClasses = MajorRepository.class)
    @Import({MajorService.class, MajorInitService.class, MajorCsvReader.class, MajorMapper.class})
    static class TestApplication {
    }
}
