package com.lifelog.domain.certification.service;
import com.lifelog.domain.certification.dto.CertificationCsvRow;
import com.lifelog.domain.certification.enums.*;
import com.lifelog.domain.certification.exception.CertificationErrorCode;
import com.lifelog.global.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Slf4j
@Component
public class CertificationCsvReader {
    private static final List<String> HEADER = List.of("name", "code", "main_category", "type", "authorized_institution_name");
    /**
     * 서버의 UTF-8 CSV를 읽고 검증한다.
     * @return 전체 데이터
     * */
    public List<CertificationCsvRow> read() {
        log.info("[CertificationCsvReader] read() - START");
        try (Reader reader = new InputStreamReader(new ClassPathResource("data/certification.csv").getInputStream(),
                StandardCharsets.UTF_8.newDecoder())) {
            var result = parse(reader);
            log.info("[CertificationCsvReader] read() - END | count: {}", result.size());
            return result;
        } catch (IOException e) {
            log.error("자격증 파일 읽기 실패", e);
            throw new CustomException(CertificationErrorCode.CSV_READ_FAILED);
        }
    }

    /** CSV 전체를 검증한다. @param reader 호출자가 관리하는 입력 @return 검증된 행 */
    public List<CertificationCsvRow> parse(Reader reader) {
        log.debug("[CertificationCsvReader] parse() - START");
        try {
            var records = tokenize(reader);
            if (records.size() < 2 || !HEADER.equals(records.getFirst().columns()))
                throw invalidFormat(1, "헤더 또는 데이터가 없습니다.");
            List<CertificationCsvRow> rows = new ArrayList<>();
            Set<String> codes = new HashSet<>();
            Set<List<String>> names = new HashSet<>();
            for (int i = 1; i < records.size(); i++) {
                var record = records.get(i);
                var c = record.columns();
                if (c.size() != 5 || c.get(0).isBlank() || c.get(2).isBlank() || c.get(3).isBlank()
                        || c.get(0).length() > 50 || c.get(1).length() > 10 || c.get(4).length() > 50)
                    throw invalidFormat(record.lineNumber(), "필수값 또는 길이가 올바르지 않습니다.");
                CertificationCsvRow row;
                try {
                    row = new CertificationCsvRow(c.get(0), c.get(1).isBlank() ? null : c.get(1),
                            CertificationMainCategory.valueOf(c.get(2)), CertificationType.valueOf(c.get(3)),
                            c.get(4).isBlank() ? null : c.get(4));
                } catch (IllegalArgumentException e) {
                    throw invalidFormat(record.lineNumber(), "정의되지 않은 ENUM입니다.");
                }
                if (row.code() != null && !codes.add(row.code()))
                    throw invalidFormat(record.lineNumber(), "중복 코드입니다.");
                if (!names.add(List.of(row.type().name(), row.name())))
                    throw invalidFormat(record.lineNumber(), "유형과 이름이 중복됩니다.");
                rows.add(row);
            }
            log.debug("[CertificationCsvReader] parse() - END | count: {}", rows.size());
            return List.copyOf(rows);
        } catch (IOException e) {
            throw new CustomException(CertificationErrorCode.CSV_READ_FAILED);
        }
    }

    /**
     * BOM·인용 필드·이스케이프·줄바꿈을 처리하고 물리 행 번호를 보존한다.
     * @param reader CSV 입력
     * @return 헤더를 포함하며 빈 줄만 제외한 레코드
     * @throws IOException 입력을 읽을 수 없는 경우
     */
    private List<CsvRecord> tokenize(Reader reader) throws IOException {
        log.debug("[CertificationCsvReader] CSV 분리 | tokenize() - START");
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
        log.debug("[CertificationCsvReader] CSV 분리 | tokenize() - END | recordCount: {}", records.size());
        return records;
    }

    /**
     * 원본의 물리 행 번호와 사유를 포함한 형식 오류를 만든다.
     * @param lineNumber 문제가 발생한 물리 행 번호
     * @param reason 오류 사유
     * @return 전역 예외 처리기로 전달할 예외
     */
    private CustomException invalidFormat(int lineNumber, String reason) {
        log.debug("[CertificationCsvReader] 형식 오류 생성 | invalidFormat() - START | line: {}", lineNumber);
        CustomException result = new CustomException(CertificationErrorCode.INVALID_CSV_FORMAT, lineNumber + "행: " + reason);
        log.debug("[CertificationCsvReader] 형식 오류 생성 | invalidFormat() - END | reason: {}", reason);
        return result;
    }

    private record CsvRecord(int lineNumber, List<String> columns) {
    }
}
