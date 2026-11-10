package com.lifelog.domain.certification.mapper;
import com.lifelog.domain.certification.dto.CertificationCsvRow;
import com.lifelog.domain.certification.entity.Certification;
import org.springframework.stereotype.Component;
@Component
public class CertificationMapper {
    /** CSV 행을 신규 엔티티로 변환한다. @param row 검증된 행 @return ID 없는 엔티티 */
    public Certification toEntity(CertificationCsvRow row) {
        return Certification.builder().name(row.name()).code(row.code()).mainCategory(row.mainCategory())
                .type(row.type()).authorizedInstitutionName(row.authorizedInstitutionName()).build();
    }
}
