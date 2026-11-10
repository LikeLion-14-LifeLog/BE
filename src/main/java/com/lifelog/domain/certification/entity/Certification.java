package com.lifelog.domain.certification.entity;
import com.lifelog.domain.certification.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.util.Objects;
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "certificate")
public class Certification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "certificate_id")
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private CertificationType type;
    @Enumerated(EnumType.STRING) @Column(name = "main_category", nullable = false, length = 50)
    private CertificationMainCategory mainCategory;
    @Column(nullable = false, length = 50)
    private String name;
    @Column(unique = true, length = 10)
    private String code;
    @Column(name = "authorized_institution_name", length = 50)
    private String authorizedInstitutionName;

    /** 식별 코드 보완과 실제 변경 대상 필드를 비교한다. @param source 원본 @return 동일 여부 */
    public boolean hasSameValues(Certification source) {
        return type == source.type && mainCategory == source.mainCategory && name.equals(source.name)
                && (source.code == null || Objects.equals(code, source.code))
                && Objects.equals(authorizedInstitutionName, source.authorizedInstitutionName);
    }
    /** PK와 기존 코드를 보존하며 원본 값을 반영한다. @param source 검증된 원본 */
    public void update(Certification source) {
        type = source.type; mainCategory = source.mainCategory; name = source.name;
        if (code == null) code = source.code;
        authorizedInstitutionName = source.authorizedInstitutionName;
    }
}
