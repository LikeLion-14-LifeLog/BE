package com.lifelog.domain.languageexam.service;

import com.lifelog.domain.languageexam.dto.LanguageExamCsvRow;
import com.lifelog.domain.languageexam.dto.LanguageExamInitResponse;
import com.lifelog.domain.languageexam.dto.LanguageExamListResponse;
import com.lifelog.domain.languageexam.dto.LanguageExamResponse;
import com.lifelog.domain.languageexam.entity.LanguageExam;
import com.lifelog.domain.languageexam.entity.LanguageType;
import com.lifelog.domain.languageexam.entity.ScoreType;
import com.lifelog.domain.languageexam.exception.LanguageExamErrorCode;
import com.lifelog.domain.languageexam.repository.LanguageExamRepository;
import com.lifelog.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LanguageExamService {

    private static final int MAX_KEYWORD_LENGTH = 20;

    private final LanguageExamRepository languageExamRepository;
    private final LanguageExamCsvReader languageExamCsvReader;

    // CSV 기준 upsert: 없으면 생성, 값이 다르면 수정, CSV에 없는 기존 데이터는 유지
    @Transactional
    public LanguageExamInitResponse initFromCsv() {
        List<LanguageExamCsvRow> rows = languageExamCsvReader.read();

        Map<Long, LanguageExam> existing = languageExamRepository.findAll().stream()
                .collect(Collectors.toMap(LanguageExam::getId, Function.identity()));

        List<LanguageExam> created = new ArrayList<>();
        int updatedCount = 0;
        int unchangedCount = 0;

        for (LanguageExamCsvRow row : rows) {
            LanguageExam languageExam = existing.get(row.languageExamId());

            if (languageExam == null) {
                created.add(new LanguageExam(row.languageExamId(), row.name(), row.languageType(), row.scoreType()));
            } else if (languageExam.hasSameValues(row.name(), row.languageType(), row.scoreType())) {
                unchangedCount++;
            } else {
                languageExam.update(row.name(), row.languageType(), row.scoreType());
                updatedCount++;
            }
        }
        languageExamRepository.saveAll(created);

        return new LanguageExamInitResponse(rows.size(), created.size(), updatedCount, unchangedCount);
    }

    // 데이터가 수십 건이라 전체 조회 후 메모리에서 필터링
    @Transactional(readOnly = true)
    public LanguageExamListResponse search(String keyword, LanguageType languageType, ScoreType scoreType) {
        String normalizedKeyword = normalizeKeyword(keyword);

        List<LanguageExamResponse> languageExams = languageExamRepository.findAll(Sort.by("id")).stream()
                .filter(exam -> normalizedKeyword == null
                        || exam.getName().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .filter(exam -> languageType == null || exam.getLanguageType() == languageType)
                .filter(exam -> scoreType == null || exam.getScoreType() == scoreType)
                .map(LanguageExamResponse::from)
                .toList();

        return LanguageExamListResponse.from(languageExams);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String trimmed = keyword.strip();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw new CustomException(LanguageExamErrorCode.INVALID_KEYWORD);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
