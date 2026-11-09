package com.lifelog.domain.major.service;

import com.lifelog.domain.major.dto.MajorCsvReadResult;
import com.lifelog.domain.major.dto.MajorCsvRow;
import com.lifelog.domain.major.enums.DegreeProcess;
import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.domain.major.enums.MajorMiddleField;
import com.lifelog.domain.major.enums.MajorSubField;
import com.lifelog.domain.major.exception.MajorErrorCode;
import com.lifelog.global.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MajorCsvReader {

    private static final String CSV_PATH = "data/major.csv";
    private static final List<String> HEADER = List.of(
            "degree_process", "main_field", "middle_field", "sub_field", "name", "code");

    /**
     * 서버에 배포된 UTF-8 전공 CSV를 트랜잭션 없이 읽고 검증한다.
     * @return 중복 제거된 원본과 원본·중복 행 수
     */
    public MajorCsvReadResult read() {
        log.info("[MajorCsvReader] 원본 읽기 | read() - START");
        try (Reader reader = new InputStreamReader(
                new ClassPathResource(CSV_PATH).getInputStream(), StandardCharsets.UTF_8)) {
            MajorCsvReadResult result = parse(reader);
            log.info("[MajorCsvReader] 원본 읽기 | read() - END | sourceRowCount: {}", result.sourceRowCount());
            return result;
        } catch (IOException e) {
            log.error("[MajorCsvReader] 원본 읽기 실패", e);
            throw new CustomException(MajorErrorCode.CSV_READ_FAILED);
        }
    }

    /**
     * CSV 전체를 검증하고 같은 코드·동일 내용만 중복으로 제외한다.
     * 전달받은 Reader의 생명주기는 호출자가 관리한다.
     * @param reader 검증할 CSV 입력
     * @return 원본·중복 행 수와 고유 코드별 데이터
     */
    public MajorCsvReadResult parse(Reader reader) {
        log.debug("[MajorCsvReader] CSV 검증 | parse() - START");
        try {
            /* (1) 따옴표·쉼표·줄바꿈을 처리하고 헤더를 검증한다. */
            List<CsvRecord> records = tokenize(reader);
            if (records.isEmpty() || !HEADER.equals(records.getFirst().columns())) {
                throw invalidFormat(1, "헤더가 올바르지 않습니다.");
            }
            /* (2) 전체 행을 검증하며 코드별 동일 중복만 제외한다. */
            Map<String, MajorCsvRow> unique = new LinkedHashMap<>();
            Map<String, Integer> firstLines = new LinkedHashMap<>();
            int duplicateCount = 0;
            for (int i = 1; i < records.size(); i++) {
                CsvRecord record = records.get(i);
                MajorCsvRow row = toRow(record);
                MajorCsvRow previous = unique.putIfAbsent(row.code(), row);
                if (previous != null) {
                    if (!previous.equals(row)) {
                        throw new CustomException(MajorErrorCode.CONFLICTING_CSV_CODE,
                                record.lineNumber() + "행: code=" + row.code()
                                        + ", 최초 " + firstLines.get(row.code()) + "행과 내용이 다릅니다.");
                    }
                    duplicateCount++;
                } else {
                    firstLines.put(row.code(), record.lineNumber());
                }
            }
            MajorCsvReadResult result = new MajorCsvReadResult(records.size() - 1,
                    duplicateCount, new ArrayList<>(unique.values()));
            log.debug("[MajorCsvReader] CSV 검증 | parse() - END | sourceRowCount: {}, duplicateRowCount: {}",
                    result.sourceRowCount(), result.duplicateRowCount());
            return result;
        } catch (IOException e) {
            log.error("[MajorCsvReader] CSV 입력 읽기 실패", e);
            throw new CustomException(MajorErrorCode.CSV_READ_FAILED);
        }
    }

    /**
     * CSV 행의 필수값·길이·열거형을 검증한다.
     * @param record 물리 시작 행 번호를 포함한 CSV 레코드
     * @return 검증된 전공 데이터
     */
    private MajorCsvRow toRow(CsvRecord record) {
        log.debug("[MajorCsvReader] 행 검증 | toRow() - START | line: {}", record.lineNumber());
        List<String> c = record.columns();
        if (c.size() != HEADER.size() || c.stream().anyMatch(String::isBlank)) {
            throw invalidFormat(record.lineNumber(), "필수 컬럼 6개를 모두 입력해야 합니다.");
        }
        if (c.get(4).length() > 100 || c.get(5).length() > 20) {
            throw invalidFormat(record.lineNumber(), "학과명은 100자, 학과코드는 20자 이하여야 합니다.");
        }
        try {
            MajorCsvRow result = new MajorCsvRow(DegreeProcess.valueOf(c.get(0)),
                    MajorMainField.valueOf(c.get(1)), MajorMiddleField.valueOf(c.get(2)),
                    MajorSubField.valueOf(c.get(3)), c.get(4), c.get(5));
            log.debug("[MajorCsvReader] 행 검증 | toRow() - END | code: {}", result.code());
            return result;
        } catch (IllegalArgumentException e) {
            throw invalidFormat(record.lineNumber(), "정의되지 않은 enum 값입니다.");
        }
    }

    /**
     * BOM·인용 필드·이스케이프·줄바꿈을 처리하고 물리 행 번호를 보존한다.
     * @param reader CSV 입력
     * @return 헤더를 포함하며 빈 줄만 제외한 레코드
     * @throws IOException 입력을 읽을 수 없는 경우
     */
    private List<CsvRecord> tokenize(Reader reader) throws IOException {
        log.debug("[MajorCsvReader] CSV 분리 | tokenize() - START");
        StringWriter buffer = new StringWriter();
        reader.transferTo(buffer);
        String text = buffer.toString().replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        List<CsvRecord> records = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean afterQuote = false;
        boolean hasContent = false;
        int lineNumber = 1;
        int recordStart = 1;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() && quoted) {
                throw invalidFormat(recordStart, "따옴표가 닫히지 않았습니다.");
            }
            char ch = i == text.length() ? '\n' : text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                        afterQuote = true;
                    }
                } else {
                    field.append(ch);
                    if (ch == '\n') {
                        lineNumber++;
                    }
                }
            } else if (ch == ',' || ch == '\n') {
                columns.add(field.toString().strip());
                field.setLength(0);
                afterQuote = false;
                if (ch == ',') {
                    hasContent = true;
                } else {
                    if (hasContent) {
                        records.add(new CsvRecord(recordStart, List.copyOf(columns)));
                    }
                    columns.clear();
                    hasContent = false;
                    recordStart = ++lineNumber;
                }
            } else if (afterQuote) {
                if (!Character.isWhitespace(ch)) {
                    throw invalidFormat(lineNumber, "닫는 따옴표 뒤에 잘못된 문자가 있습니다.");
                }
            } else if (ch == '"') {
                if (!field.toString().isBlank()) {
                    throw invalidFormat(lineNumber, "따옴표는 필드 시작에만 사용할 수 있습니다.");
                }
                field.setLength(0);
                quoted = true;
                hasContent = true;
            } else {
                field.append(ch);
                hasContent |= !Character.isWhitespace(ch);
            }
        }
        log.debug("[MajorCsvReader] CSV 분리 | tokenize() - END | recordCount: {}", records.size());
        return records;
    }

    /**
     * 원본의 물리 행 번호와 사유를 포함한 형식 오류를 만든다.
     * @param lineNumber 문제가 발생한 물리 행 번호
     * @param reason 오류 사유
     * @return 전역 예외 처리기로 전달할 예외
     */
    private CustomException invalidFormat(int lineNumber, String reason) {
        log.debug("[MajorCsvReader] 형식 오류 생성 | invalidFormat() - START | line: {}", lineNumber);
        CustomException result = new CustomException(MajorErrorCode.INVALID_CSV_FORMAT, lineNumber + "행: " + reason);
        log.debug("[MajorCsvReader] 형식 오류 생성 | invalidFormat() - END | reason: {}", reason);
        return result;
    }

    private record CsvRecord(int lineNumber, List<String> columns) {
    }
}
