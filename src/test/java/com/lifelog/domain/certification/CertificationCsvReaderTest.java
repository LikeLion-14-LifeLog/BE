package com.lifelog.domain.certification;
import com.lifelog.domain.certification.service.CertificationCsvReader;
import com.lifelog.domain.certification.enums.*;
import com.lifelog.global.exception.CustomException;
import org.junit.jupiter.api.*;
import java.io.StringReader;
import static org.assertj.core.api.Assertions.*;
class CertificationCsvReaderTest {
    private final CertificationCsvReader reader = new CertificationCsvReader();
    private static final String HEADER = "name,code,main_category,type,authorized_institution_name\n";
    @Test @DisplayName("배포 CSV의 전체 건수와 nullable 필드를 검증한다")
    void 배포원본_성공() {
        // given / when
        var rows = reader.read();
        // then
        assertThat(rows).hasSize(1424);
        assertThat(rows.stream().filter(r -> r.code() == null).count()).isEqualTo(616);
        assertThat(rows.stream().filter(r -> r.authorizedInstitutionName() == null).count()).isEqualTo(598);
        assertThat(rows.getFirst().name()).isEqualTo("무역영어 1급");
        assertThat(CertificationMainCategory.values()).hasSize(23);
        assertThat(CertificationType.values()).hasSize(4);
        assertThat(CertificationMainCategory.LAW_POLICE_FIRE_CORRECTIONS.getKorean()).isEqualTo("법률·경찰·소방·교도");
    }
    @Test @DisplayName("BOM·인용 쉼표·빈 필드를 처리한다")
    void 인용필드_NULL_성공() {
        // given / when
        var rows = reader.parse(new StringReader("\uFEFF" + HEADER + "\"자격, 명\",,COMMON,PRIVATE, "));
        // then
        assertThat(rows.getFirst().name()).isEqualTo("자격, 명");
        assertThat(rows.getFirst().code()).isNull();
        assertThat(rows.getFirst().authorizedInstitutionName()).isNull();
    }
    @Test @DisplayName("빈 파일·중복 식별·잘못된 ENUM과 길이를 거부한다")
    void 원본검증_실패() {
        // given / when / then
        for (String data : new String[]{"", HEADER, HEADER + "이름,,UNKNOWN,PRIVATE,",
                HEADER + "이름,,COMMON,PRIVATE,\n이름,,COMMON,PRIVATE,",
                HEADER + "가".repeat(51) + ",,COMMON,PRIVATE,", HEADER + "\"닫히지 않음"}) {
            assertThatThrownBy(() -> reader.parse(new StringReader(data))).isInstanceOf(CustomException.class);
        }
    }
}
