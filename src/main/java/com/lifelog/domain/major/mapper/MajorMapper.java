package com.lifelog.domain.major.mapper;

import com.lifelog.domain.major.dto.MajorCsvRow;
import com.lifelog.domain.major.entity.Major;
import org.springframework.stereotype.Component;

@Component
public class MajorMapper {

    /**
     * 검증된 CSV 행을 신규 전공 엔티티로 변환한다.
     * @param row 검증된 전공 데이터
     * @return ID가 아직 없는 전공 엔티티
     */
    public Major toEntity(MajorCsvRow row) {
        return Major.builder().degreeProcess(row.degreeProcess())
                .mainField(row.mainField()).middleField(row.middleField()).subField(row.subField())
                .name(row.name()).code(row.code()).build();
    }
}
