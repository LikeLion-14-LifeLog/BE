package com.lifelog.domain.major.service;

import com.lifelog.domain.major.dto.MajorCsvReadResult;
import com.lifelog.domain.major.exception.MajorErrorCode;
import com.lifelog.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MajorCsvReaderTest {

    private static final String HEADER = "degree_process,main_field,middle_field,sub_field,name,code\n";
    private static final String ROW = "COLLEGE,HUMANITIES,LANGUAGES_LITERATURE,JAPANESE_LANGUAGE,일본어과,C01";
    private final MajorCsvReader reader = new MajorCsvReader();

    @Test
    @DisplayName("배포 원본의 전체·중복·처리 대상 건수가 일치한다")
    void 실제_파일_집계_성공() {
        // given / when
        MajorCsvReadResult result = reader.read();
        // then
        assertThat(result.sourceRowCount()).isEqualTo(37656);
        assertThat(result.duplicateRowCount()).isEqualTo(2);
        assertThat(result.rows()).hasSize(37654);
        assertThat(result.rows()).extracting(row -> row.code()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("BOM과 CRLF, 인용 필드의 쉼표와 이스케이프를 처리한다")
    void 따옴표_쉼표_BOM_성공() {
        // given
        String csv = "\uFEFF" + HEADER + ROW.replace("일본어과", "\"일본어, \"\"통역\"\"과\"") + "\n\n";
        // when
        MajorCsvReadResult result = reader.parse(new StringReader(csv.replace("\n", "\r\n")));
        // then
        assertThat(result.sourceRowCount()).isEqualTo(1);
        assertThat(result.rows().getFirst().name()).isEqualTo("일본어, \"통역\"과");
    }

    @Test
    @DisplayName("따옴표 안의 줄바꿈은 하나의 데이터 레코드로 처리한다")
    void 인용_필드_줄바꿈_성공() {
        // given
        String csv = HEADER + ROW.replace("일본어과", "\"일본어\n통역과\"");
        // when
        MajorCsvReadResult result = reader.parse(new StringReader(csv));
        // then
        assertThat(result.sourceRowCount()).isEqualTo(1);
        assertThat(result.rows().getFirst().name()).isEqualTo("일본어\n통역과");
    }

    @Test
    @DisplayName("같은 코드·동일 내용은 처리 대상에서 제외한다")
    void 동일_내용_중복_제외_성공() {
        // given / when
        MajorCsvReadResult result = reader.parse(new StringReader(HEADER + ROW + "\n" + ROW));
        // then
        assertThat(result.sourceRowCount()).isEqualTo(2);
        assertThat(result.duplicateRowCount()).isEqualTo(1);
        assertThat(result.rows()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "wrong,header\n", "name,code,sub_field,middle_field,main_field,degree_process\n"})
    @DisplayName("헤더가 없거나 순서가 다르면 원본 오류다")
    void 헤더_오류_실패(String csv) {
        // given / when / then
        assertInvalid(csv);
    }

    @ParameterizedTest
    @MethodSource("invalidEnums")
    @DisplayName("학위과정과 대·중·소분류의 정의되지 않은 값은 거부한다")
    void 잘못된_enum_실패(String row) {
        // given / when / then
        assertInvalid(HEADER + row);
    }

    static Stream<String> invalidEnums() {
        return IntStream.range(0, 4).mapToObj(index -> {
            String[] columns = ROW.split(",", -1);
            columns[index] = "UNKNOWN";
            return String.join(",", columns);
        });
    }

    @ParameterizedTest
    @MethodSource("emptyFields")
    @DisplayName("모든 필수 컬럼의 빈 값과 공백을 거부한다")
    void 빈_값_실패(String row) {
        // given / when / then
        assertInvalid(HEADER + row);
    }

    static Stream<String> emptyFields() {
        return IntStream.range(0, 6).mapToObj(index -> {
            String[] columns = ROW.split(",", -1);
            columns[index] = "   ";
            return String.join(",", columns);
        });
    }

    @Test
    @DisplayName("학과명 100자·코드 20자 경계는 허용하고 초과는 거부한다")
    void 길이_경계_성공_초과_실패() {
        // given
        String valid = ROW.replace("일본어과", "가".repeat(100)).replace("C01", "C".repeat(20));
        // when / then
        assertThat(reader.parse(new StringReader(HEADER + valid)).rows()).hasSize(1);
        assertInvalid(HEADER + ROW.replace("일본어과", "가".repeat(101)));
        assertInvalid(HEADER + ROW.replace("C01", "C".repeat(21)));
    }

    @Test
    @DisplayName("동일 코드의 내용 충돌은 코드와 물리 행 번호를 포함해 실패한다")
    void 내용_충돌_실패() {
        // given
        String csv = HEADER + ROW + "\n" + ROW.replace("일본어과", "다른 학과");
        // when / then
        assertThatThrownBy(() -> reader.parse(new StringReader(csv)))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(MajorErrorCode.CONFLICTING_CSV_CODE);
                    assertThat(e.getData().toString()).contains("C01", "3행", "2행");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"닫히지 않음", "\"닫힘\"문자", "중간\"따옴표"})
    @DisplayName("닫히지 않거나 잘못 배치한 따옴표는 거부한다")
    void 따옴표_형식_실패(String name) {
        // given / when / then
        assertInvalid(HEADER + ROW.replace("일본어과", name));
    }

    @Test
    @DisplayName("컬럼이 부족하거나 많으면 거부한다")
    void 컬럼_수_실패() {
        // given / when / then
        assertInvalid(HEADER + ROW + ",extra");
        assertInvalid(HEADER + ROW.substring(0, ROW.lastIndexOf(',')));
    }

    private void assertInvalid(String csv) {
        assertThatThrownBy(() -> reader.parse(new StringReader(csv)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MajorErrorCode.INVALID_CSV_FORMAT));
    }
}
