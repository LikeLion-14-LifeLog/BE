package com.lifelog.domain.jobposting.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class JobCodeMapperTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            // 131x~133x: 앞 4자리 그대로
            "131100, 1311", "132001, 1320", "133101, 1331", "133207, 1332", "133302, 1333", "133900, 1339",
            // 134x~137x: 예외 변환표
            "134100, 1342", "134200, 1350", "134900, 1349", "135101, 1341", "135102, 1341",
            "136101, 1343", "136102, 1343", "136103, 1343", "136200, 1344", "137000, 1360"
    })
    void 고용24_코드를_8차_직무_코드로_변환(String externalJobCode, String expected) {
        assertThat(JobCodeMapper.toJobCode(externalJobCode)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1332", "13320A", "1332000"})
    void 형식이_다르면_null(String externalJobCode) {
        assertThat(JobCodeMapper.toJobCode(externalJobCode)).isNull();
    }
}
