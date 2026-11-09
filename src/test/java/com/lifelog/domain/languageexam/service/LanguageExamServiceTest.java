package com.lifelog.domain.languageexam.service;

import com.lifelog.domain.languageexam.dto.LanguageExamInitResponse;
import com.lifelog.domain.languageexam.dto.LanguageExamListResponse;
import com.lifelog.domain.languageexam.dto.LanguageExamResponse;
import com.lifelog.domain.languageexam.entity.LanguageExam;
import com.lifelog.domain.languageexam.entity.LanguageType;
import com.lifelog.domain.languageexam.entity.ScoreType;
import com.lifelog.domain.languageexam.repository.LanguageExamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class LanguageExamServiceTest {

    private static final int CSV_ROW_COUNT = 9;

    @Autowired
    private LanguageExamService languageExamService;

    @Autowired
    private LanguageExamRepository languageExamRepository;

    @BeforeEach
    void setUp() {
        // 로컬 DB에 이미 데이터가 있어도 테스트가 독립적으로 돌도록 비움 (트랜잭션 롤백됨)
        languageExamRepository.deleteAllInBatch();
    }

    @Test
    void 처음_init하면_전부_created() {
        LanguageExamInitResponse response = languageExamService.initFromCsv();

        assertThat(response.totalCount()).isEqualTo(CSV_ROW_COUNT);
        assertThat(response.createdCount()).isEqualTo(CSV_ROW_COUNT);
        assertThat(response.updatedCount()).isZero();
        assertThat(response.unchangedCount()).isZero();
    }

    @Test
    void 두번_init해도_개수와_ID가_같고_전부_unchanged() {
        languageExamService.initFromCsv();
        List<Long> firstIds = findAllIds();

        LanguageExamInitResponse second = languageExamService.initFromCsv();

        assertThat(second.createdCount()).isZero();
        assertThat(second.updatedCount()).isZero();
        assertThat(second.unchangedCount()).isEqualTo(CSV_ROW_COUNT);
        assertThat(languageExamRepository.count()).isEqualTo(CSV_ROW_COUNT);
        assertThat(findAllIds()).isEqualTo(firstIds);
    }

    @Test
    void 값이_다르면_updated() {
        languageExamService.initFromCsv();
        languageExamRepository.findById(1L).orElseThrow()
                .update("OLD TOEIC", LanguageType.ENGLISH, ScoreType.GRADE);
        languageExamRepository.flush();

        LanguageExamInitResponse response = languageExamService.initFromCsv();

        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(response.unchangedCount()).isEqualTo(CSV_ROW_COUNT - 1);
        assertThat(languageExamRepository.findById(1L).orElseThrow().getName()).isEqualTo("TOEIC");
    }

    @Test
    void CSV에_없는_기존_데이터는_삭제하지_않음() {
        languageExamRepository.saveAndFlush(LanguageExam.builder()
                .id(100L)
                .name("CSV 밖 시험")
                .languageType(LanguageType.JAPANESE)
                .scoreType(ScoreType.GRADE)
                .build());

        languageExamService.initFromCsv();

        assertThat(languageExamRepository.findById(100L)).isPresent();
        assertThat(languageExamRepository.count()).isEqualTo(CSV_ROW_COUNT + 1);
    }

    @Test
    void CSV의_ID를_그대로_저장() {
        languageExamService.initFromCsv();

        assertThat(languageExamRepository.findById(1L).orElseThrow().getName()).isEqualTo("TOEIC");
        assertThat(languageExamRepository.findById(9L).orElseThrow().getScoreType()).isEqualTo(ScoreType.NONE);
    }

    @Test
    void keyword_앞뒤공백_제거_대소문자_무시() {
        languageExamService.initFromCsv();

        LanguageExamListResponse response = languageExamService.search("  toeic ", null, null);

        assertThat(names(response)).containsExactly("TOEIC", "TOEIC Speaking", "TOEIC Writing");
        assertThat(response.totalCount()).isEqualTo(3);
    }

    @Test
    void keyword와_enum_조건_조합() {
        languageExamService.initFromCsv();

        LanguageExamListResponse response =
                languageExamService.search("toeic", LanguageType.ENGLISH, ScoreType.NUMERIC_AND_GRADE);

        assertThat(names(response)).containsExactly("TOEIC Speaking", "TOEIC Writing");
    }

    @Test
    void 조건이_없거나_공백_keyword면_전체를_ID_오름차순으로() {
        languageExamService.initFromCsv();

        LanguageExamListResponse noCondition = languageExamService.search(null, null, null);
        LanguageExamListResponse blankKeyword = languageExamService.search("   ", null, null);

        assertThat(noCondition.totalCount()).isEqualTo(CSV_ROW_COUNT);
        assertThat(blankKeyword.totalCount()).isEqualTo(CSV_ROW_COUNT);
        assertThat(noCondition.languageExams())
                .extracting(LanguageExamResponse::languageExamId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
    }

    private List<Long> findAllIds() {
        return languageExamRepository.findAll().stream()
                .map(LanguageExam::getId)
                .sorted()
                .toList();
    }

    private List<String> names(LanguageExamListResponse response) {
        return response.languageExams().stream()
                .map(LanguageExamResponse::name)
                .toList();
    }
}
