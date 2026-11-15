package com.lifelog.domain.specprofile.dto;

import java.util.List;
import java.util.Map;

/** 특정 조사 시점까지 확인한 스펙. 필드 목록과 연결된 관측값을 함께 보존한다. */
public record KeepSalaryAccumulatedSpec(
        Map<String, List<String>> fields,
        List<AcademicObservation> academics,
        List<EnglishObservation> exams,
        List<JobObservation> jobs
) {
    public record AcademicObservation(int year, boolean direct, String type, String field, String majorCode,
                                      String location, String gpa, String scale) {}
    public record EnglishObservation(int year, boolean direct, String type, String score) {}
    public record JobObservation(int year, boolean direct, String code) {}
}
