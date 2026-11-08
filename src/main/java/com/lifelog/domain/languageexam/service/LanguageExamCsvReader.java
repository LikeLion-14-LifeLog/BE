package com.lifelog.domain.languageexam.service;

import com.lifelog.domain.languageexam.dto.LanguageExamCsvRow;
import com.lifelog.domain.languageexam.entity.LanguageType;
import com.lifelog.domain.languageexam.entity.ScoreType;
import com.lifelog.domain.languageexam.exception.LanguageExamErrorCode;
import com.lifelog.global.exception.CustomException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class LanguageExamCsvReader {

    private static final String CSV_PATH = "data/language_exam.csv";
    private static final String HEADER = "language_exam_id,name,language_type,score_type";
    private static final String BOM = "﻿";
    private static final int COLUMN_COUNT = 4;
    private static final int MAX_NAME_LENGTH = 50;

    public List<LanguageExamCsvRow> read() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(CSV_PATH).getInputStream(), StandardCharsets.UTF_8))) {
            return parse(reader.lines().toList());
        } catch (IOException e) {
            throw new CustomException(LanguageExamErrorCode.CSV_READ_FAILED);
        }
    }

    private List<LanguageExamCsvRow> parse(List<String> lines) {
        if (lines.isEmpty() || !HEADER.equals(removeBom(lines.get(0)).strip())) {
            throw invalidFormat(1, "헤더가 올바르지 않습니다.");
        }

        List<LanguageExamCsvRow> rows = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        Set<String> names = new HashSet<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty()) {
                continue;
            }

            int lineNumber = i + 1;
            LanguageExamCsvRow row = parseLine(line, lineNumber);

            if (!ids.add(row.languageExamId())) {
                throw invalidFormat(lineNumber, "중복된 language_exam_id입니다.");
            }
            if (!names.add(row.name())) {
                throw invalidFormat(lineNumber, "중복된 name입니다.");
            }
            rows.add(row);
        }
        return rows;
    }

    private LanguageExamCsvRow parseLine(String line, int lineNumber) {
        String[] columns = line.split(",", -1);
        if (columns.length != COLUMN_COUNT) {
            throw invalidFormat(lineNumber, "컬럼 수가 올바르지 않습니다.");
        }

        String name = columns[1].strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw invalidFormat(lineNumber, "name은 1~50자여야 합니다.");
        }

        try {
            return new LanguageExamCsvRow(
                    Long.parseLong(columns[0].strip()),
                    name,
                    LanguageType.valueOf(columns[2].strip()),
                    ScoreType.valueOf(columns[3].strip())
            );
        } catch (IllegalArgumentException e) {
            // NumberFormatException 포함: ID가 숫자가 아니거나 enum 값이 없는 경우
            throw invalidFormat(lineNumber, "값이 올바르지 않습니다.");
        }
    }

    private String removeBom(String line) {
        return line.startsWith(BOM) ? line.substring(1) : line;
    }

    private CustomException invalidFormat(int lineNumber, String reason) {
        return new CustomException(LanguageExamErrorCode.INVALID_CSV_FORMAT, lineNumber + "행: " + reason);
    }
}
