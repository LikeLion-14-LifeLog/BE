package com.lifelog.domain.major.entity;

import com.lifelog.domain.major.enums.DegreeProcess;
import com.lifelog.domain.major.enums.MajorMainField;
import com.lifelog.domain.major.enums.MajorMiddleField;
import com.lifelog.domain.major.enums.MajorSubField;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "major")
public class Major {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "major_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "main_field", nullable = false, length = 50)
    private MajorMainField mainField;

    @Enumerated(EnumType.STRING)
    @Column(name = "middle_field", nullable = false, length = 100)
    private MajorMiddleField middleField;

    @Enumerated(EnumType.STRING)
    @Column(name = "sub_field", nullable = false, length = 100)
    private MajorSubField subField;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "degree_process", nullable = false, length = 50)
    private DegreeProcess degreeProcess;

    /**
     * 코드로 식별한 전공의 변경 가능한 값이 같은지 확인한다.
     * @param source 비교할 원본 전공
     * @return 분류·학과명·학위과정이 모두 같으면 true
     */
    public boolean hasSameValues(Major source) {
        return mainField == source.mainField && middleField == source.middleField
                && subField == source.subField && name.equals(source.name)
                && degreeProcess == source.degreeProcess;
    }

    /**
     * ID와 학과코드를 유지하고 변경 가능한 전공 정보만 갱신한다.
     * @param source 반영할 원본 전공
     */
    public void update(Major source) {
        this.mainField = source.mainField;
        this.middleField = source.middleField;
        this.subField = source.subField;
        this.name = source.name;
        this.degreeProcess = source.degreeProcess;
    }
}
