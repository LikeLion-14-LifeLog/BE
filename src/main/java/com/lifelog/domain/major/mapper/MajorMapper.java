package com.lifelog.domain.major.mapper;

import com.lifelog.domain.major.dto.MajorCsvRow;
import com.lifelog.domain.major.dto.MajorListResDto;
import com.lifelog.domain.major.dto.MajorResDto;
import com.lifelog.domain.major.entity.Major;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class MajorMapper {

    /**
     * 전공 엔티티를 조회 응답으로 변환한다.
     * @param major 조회한 전공
     * @return 중·소계열 한글명을 포함한 전공 응답
     */
    public MajorResDto toMajorResDto(Major major) {
        return MajorResDto.create(major.getId(), major.getMainField(), major.getMiddleField().getKorean(),
                major.getSubField().getKorean(), major.getName(), major.getCode(), major.getDegreeProcess());
    }

    /**
     * 현재 페이지의 전공들을 목록 응답으로 변환한다.
     * @param majors 현재 페이지의 전공
     * @return 빈 배열을 허용하는 전공 목록
     */
    public MajorListResDto toMajorListResDto(List<Major> majors) {
        return MajorListResDto.create(majors.stream().map(this::toMajorResDto).toList());
    }

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
