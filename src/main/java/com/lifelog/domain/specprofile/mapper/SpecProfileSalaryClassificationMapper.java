package com.lifelog.domain.specprofile.mapper;

import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationSnapshot;
import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData;
import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData.AcademicCondition;
import com.lifelog.domain.specprofile.dto.SpecProfileSalaryClassificationData.LanguageCondition;
import org.springframework.stereotype.Component;

@Component
public class SpecProfileSalaryClassificationMapper {
    /**
     * 직무 Entity와 요청 스냅샷에서 CSV 분류 조건을 만든다.
     * @param job 스냅샷의 jobId에 해당하는 직무
     * @param snapshot 저장된 전체 스펙
     * @return 항목별 원 조건값과 자격증 목록 크기
     */
    public SpecProfileSalaryClassificationData toClassificationData(Job job, SpecProfileAnalyzationSnapshot snapshot) {
        return new SpecProfileSalaryClassificationData(job.getSubCategoryCode(), job.getSubCategoryName(),
                snapshot.academicAbilities().stream().map(academic -> new AcademicCondition(
                        academic.type(), academic.location(), academic.majorAffiliation(), academic.majorName(),
                        academic.universityGrade(), academic.universityStandardGrade(), null)).toList(),
                snapshot.languageExamScores().stream().map(score -> new LanguageCondition(
                        score.languageExamId(), score.languageExamName(), score.scoreNumeric(), score.scoreGrade(), null, null)).toList(),
                snapshot.certificates().size());
    }
}
