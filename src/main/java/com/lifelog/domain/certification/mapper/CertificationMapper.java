package com.lifelog.domain.certification.mapper;
import com.lifelog.domain.certification.dto.CertificationCsvRow;
import com.lifelog.domain.certification.dto.CertificationResDto;
import com.lifelog.domain.certification.dto.CertificationListResDto;
import java.util.List;
import com.lifelog.domain.certification.entity.Certification;
import org.springframework.stereotype.Component;
@Component
public class CertificationMapper {
    /**
     * 자격증을 원본 명칭과 한글 대분류명을 포함한 응답으로 변환한다.
     * @param certification 조회된 자격증
     * @return 자격증 응답
     */
    public CertificationResDto toCertificationResDto(Certification certification) {
        return CertificationResDto.create(certification.getId(), certification.getName(), certification.getCode(),
                certification.getType(), certification.getMainCategory().getKorean(),
                certification.getAuthorizedInstitutionName());
    }

    /**
     * 현재 페이지의 자격증을 목록 응답으로 변환한다.
     * @param certifications 조회된 자격증 목록
     * @return 자격증 목록 응답
     */
    public CertificationListResDto toCertificationListResDto(List<Certification> certifications) {
        return CertificationListResDto.create(certifications.stream().map(this::toCertificationResDto).toList());
    }

    /** CSV 행을 신규 엔티티로 변환한다. @param row 검증된 행 @return ID 없는 엔티티 */
    public Certification toEntity(CertificationCsvRow row) {
        return Certification.builder().name(row.name()).code(row.code()).mainCategory(row.mainCategory())
                .type(row.type()).authorizedInstitutionName(row.authorizedInstitutionName()).build();
    }
}
