package com.lifelog.domain.job.service;

import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.job.exception.JobErrorCode;
import com.lifelog.global.exception.CustomException;
import org.springframework.beans.factory.annotation.Value;
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
public class JobCsvReader {

    // 직무 기준 CSV 경로 (classpath). 설정 job.csv-path로 관리, 기본값은 한국고용직업분류 2025
    // 테스트 리소스의 application.yaml이 메인 설정을 가리므로 기본값을 함께 둠
    // job_2025.csv 출처: 고용노동부 고시 제2024-63호(2024.12.12.) 한국고용직업분류 2025 개정 고시 첨부
    //   "02.한국고용직업분류 2025 개정 항목표 및 신구연계표.xlsx" 첫 번째 시트(항목표), 세분류 495개
    //   https://www.moel.go.kr/info/lawinfo/instruction/view.do?bbs_seq=20241200770
    // 원본과 차이: 이름 끝 공백(전각 U+3000 포함) 제거, 미분류(9999) 행 추가, 합계 행 제외
    // CSV에는 주석을 넣지 않음 (이 리더가 헤더 외 주석 줄을 읽지 못함)
    static final String DEFAULT_CSV_PATH = "data/job_2025.csv";

    private final String csvPath;

    public JobCsvReader(@Value("${job.csv-path:" + DEFAULT_CSV_PATH + "}") String csvPath) {
        this.csvPath = csvPath;
    }

    private static final String HEADER = "main_category_code,main_category_name,middle_category_code,"
            + "middle_category_name,sub_category_code,sub_category_name,code,name";
    private static final String BOM = "﻿";
    private static final int COLUMN_COUNT = 8;
    private static final int MAX_NAME_LENGTH = 100;

    public List<Job> read() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(csvPath).getInputStream(), StandardCharsets.UTF_8))) {
            return parse(reader.lines().toList());
        } catch (IOException e) {
            throw new CustomException(JobErrorCode.CSV_READ_FAILED);
        }
    }

    private List<Job> parse(List<String> lines) {
        if (lines.isEmpty() || !HEADER.equals(removeBom(lines.get(0)).strip())) {
            throw invalidFormat(1, "헤더가 올바르지 않습니다.");
        }

        List<Job> jobs = new ArrayList<>();
        Set<String> codes = new HashSet<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty()) {
                continue;
            }

            int lineNumber = i + 1;
            Job job = parseLine(line, lineNumber);
            if (!codes.add(job.getCode())) {
                throw invalidFormat(lineNumber, "중복된 code입니다.");
            }
            jobs.add(job);
        }
        return jobs;
    }

    private Job parseLine(String line, int lineNumber) {
        List<String> columns = splitColumns(line, lineNumber);
        if (columns.size() != COLUMN_COUNT) {
            throw invalidFormat(lineNumber, "컬럼 수가 올바르지 않습니다.");
        }

        String mainCode = columns.get(0);
        String middleCode = columns.get(2);
        String subCode = columns.get(4);
        String code = columns.get(6);

        // 미분류처럼 대분류 코드가 없으면 중·소분류도 모두 비어 있어야 함
        boolean uncategorized = mainCode.isEmpty();
        if (uncategorized) {
            if (!middleCode.isEmpty() || !columns.get(3).isEmpty()
                    || !subCode.isEmpty() || !columns.get(5).isEmpty()
                    || !code.matches("\\d{4}")) {
                throw invalidFormat(lineNumber, "대분류 코드가 없으면 중·소분류는 비어 있어야 합니다.");
            }
        } else if (!mainCode.matches("\\d")
                // 분류 코드는 숫자이고, 하위 코드는 상위 코드로 시작해야 함
                || !middleCode.matches("\\d{2}") || !middleCode.startsWith(mainCode)
                || !subCode.matches("\\d{3}") || !subCode.startsWith(middleCode)
                || !code.matches("\\d{4}") || !code.startsWith(subCode)) {
            throw invalidFormat(lineNumber, "분류 코드가 올바르지 않습니다.");
        }
        for (int nameIndex : uncategorized ? new int[]{1, 7} : new int[]{1, 3, 5, 7}) {
            String name = columns.get(nameIndex);
            if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
                throw invalidFormat(lineNumber, "이름은 1~100자여야 합니다.");
            }
        }

        return Job.builder()
                .mainCategoryCode(emptyToNull(mainCode))
                .mainCategoryName(columns.get(1))
                .middleCategoryCode(emptyToNull(middleCode))
                .middleCategoryName(emptyToNull(columns.get(3)))
                .subCategoryCode(emptyToNull(subCode))
                .subCategoryName(emptyToNull(columns.get(5)))
                .code(code)
                .name(columns.get(7))
                .build();
    }

    private String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    // 큰따옴표로 감싼 값 안의 쉼표 처리 (예: "경찰관, 소방관 및 교도관")
    private List<String> splitColumns(String line, int lineNumber) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                columns.add(current.toString().strip());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (quoted) {
            throw invalidFormat(lineNumber, "따옴표가 닫히지 않았습니다.");
        }
        columns.add(current.toString().strip());
        return columns;
    }

    private String removeBom(String line) {
        return line.startsWith(BOM) ? line.substring(1) : line;
    }

    private CustomException invalidFormat(int lineNumber, String reason) {
        return new CustomException(JobErrorCode.INVALID_CSV_FORMAT, lineNumber + "행: " + reason);
    }
}
