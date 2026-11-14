package com.lifelog.domain.specprofile.mapper;

import com.lifelog.domain.major.dto.MajorCsvRow;
import com.lifelog.domain.major.service.MajorCsvReader;
import com.lifelog.domain.job.service.JobCsvReader;
import com.lifelog.preprocessing.keep2.enums.UniversityType;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.text.Normalizer;
import java.util.*;

@Component
@RequiredArgsConstructor
public class KeepSalaryCodeMapper {
    private final MajorCsvReader majorReader;

    /**
     * 요청당 한 번 전공 사전과 2025 직업 소분류 사전을 읽는다.
     * KEEP의 일반 컬럼과 history는 이미 KECO 2025로 변환되어 있다.
     * @return 미분류를 제외한 불변 코드 사전
     */
    public Codebook loadCodebook() {
        Map<String, String> jobs = new HashMap<>();
        new JobCsvReader("data/job_2025.csv").read().forEach(job -> {
            if (job.getSubCategoryCode() != null) {
                jobs.put(job.getCode(), job.getSubCategoryCode());
                jobs.put(job.getSubCategoryCode(), job.getSubCategoryCode());
            }
        });
        Map<String, MajorCsvRow> majors = new HashMap<>();
        majorReader.read().rows().forEach(row -> majors.put(row.code(), row));
        return new Codebook(Map.copyOf(jobs), Map.copyOf(majors));
    }

    /** 전공·시험명의 유니코드와 공백을 정규화한다. 학과 접미사나 부분 문자열은 제거하지 않는다. */
    public static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /** API 시험명을 CSV 시험 ENUM 이름으로 변환한다. ordinal 값은 사용하지 않는다. */
    public static String examType(String value) {
        return switch (normalize(value).replace(' ', '_')) {
            case "TOEIC", "TOEFL", "TEPS", "IELTS", "OPIC", "TOEIC_SPEAKING", "TOEIC_WRITING" -> normalize(value).replace(' ', '_');
            case "NONE", "영어_시험_본_적_없음" -> "NONE";
            default -> "OTHER";
        };
    }

    /** 대학 세부 유형을 현재 도메인의 학력 유형으로 변환한다. */
    public static String universityType(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String type = UniversityType.valueOf(value).name();
            return type.startsWith("COLLEGE_") ? "COLLEGE" : type.startsWith("UNIVERSITY_") ? "UNIVERSITY" : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** 대학원 과정으로 학위 유형을 판별하며 통합·비학위 과정은 일반 대학원으로 보존한다. */
    public static String graduateType(String course) {
        if (course == null || course.isBlank()) return null;
        return switch (course) {
            case "MASTER", "PROFESSIONAL_TECHNICAL_MASTER", "INTEGRATED_BACHELOR_MASTER" -> "GRADUATE_SCHOOL_MASTER";
            case "DOCTORAL" -> "GRADUATE_SCHOOL_DOCTORAL";
            case "INTEGRATED_MASTER_DOCTORAL", "NON_DEGREE" -> "GRADUATE_SCHOOL";
            default -> null;
        };
    }

    public record Codebook(Map<String, String> jobCategories, Map<String, MajorCsvRow> majors) {}
}
