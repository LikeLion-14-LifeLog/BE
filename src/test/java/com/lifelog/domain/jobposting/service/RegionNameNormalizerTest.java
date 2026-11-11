package com.lifelog.domain.jobposting.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RegionNameNormalizerTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', textBlock = """
            서울 강남구               | 서울 강남구
            서울특별시 강남구         | 서울 강남구
            경기도 화성시 만세구      | 경기 화성시 만세구
            경기 성남시 분당구        | 경기 성남시 분당구
            인천광역시 연수구         | 인천 연수구
            부산광역시 해운대구       | 부산 해운대구
            대구광역시 수성구         | 대구 수성구
            광주광역시 서구           | 광주 서구
            대전광역시 유성구         | 대전 유성구
            울산광역시 남구           | 울산 남구
            세종특별자치시            | 세종
            강원특별자치도 춘천시     | 강원 춘천시
            강원도 원주시             | 강원 원주시
            충청북도 청주시           | 충북 청주시
            충청남도 천안시           | 충남 천안시
            전북특별자치도 전주시     | 전북 전주시
            전라북도 익산시           | 전북 익산시
            전라남도 여수시           | 전남 여수시
            경상북도 포항시           | 경북 포항시
            경상남도 창원시           | 경남 창원시
            제주특별자치도 제주시     | 제주 제주시
            """)
    void 시도를_짧은_이름으로_통일(String input, String expected) {
        assertThat(RegionNameNormalizer.normalize(input)).isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    void null이면_null() {
        assertThat(RegionNameNormalizer.normalize(null)).isNull();
    }
}
