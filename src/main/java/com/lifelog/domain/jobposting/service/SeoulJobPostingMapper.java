package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.dto.SeoulJobRow;
import com.lifelog.domain.jobposting.entity.CareerType;
import com.lifelog.domain.jobposting.entity.DeadlineType;
import com.lifelog.domain.jobposting.entity.EmploymentType;
import com.lifelog.domain.jobposting.entity.JobPosting;
import com.lifelog.domain.jobposting.entity.JobPostingPay;
import com.lifelog.domain.jobposting.entity.JobPostingRequirement;
import com.lifelog.domain.jobposting.entity.JobPostingStatus;
import com.lifelog.domain.jobposting.entity.PayPeriod;
import com.lifelog.domain.jobposting.entity.PayPolicy;
import com.lifelog.domain.jobposting.entity.PayStage;
import com.lifelog.domain.jobposting.entity.RequirementPriority;
import com.lifelog.domain.jobposting.entity.RequirementType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// 서울 일자리포털 원본 한 건을 job_posting·급여·자격요건으로 변환
@Component
public class SeoulJobPostingMapper {

    public static final String SOURCE = "SEOUL_JOB";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yy-MM-dd");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{2}-\\d{2}-\\d{2}");
    private static final Pattern POSTAL_PREFIX = Pattern.compile("^\\(\\d{5}\\)\\s*");
    private static final Pattern AMOUNT_MIN = Pattern.compile("([\\d,]+)원 이상");
    private static final Pattern AMOUNT_MAX = Pattern.compile("~\\s*([\\d,]+)원 이하");
    private static final String UNTIL_FILLED = "채용시까지";
    private static final String NO_EDUCATION_REQUIRED = "00";
    // 마감일은 그날 끝까지 유효 (TIMESTAMP 정밀도 반올림을 피하려고 초 단위까지만)
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    public JobPostingSyncItem toItem(SeoulJobRow row, LocalDate today) {
        String closeDt = trimToNull(row.closeDt());
        LocalDate closeDate = parseDate(closeDt);
        LocalDateTime closesAt = closeDate == null ? null : closeDate.atTime(END_OF_DAY);
        JobPostingStatus status = closeDate != null && closeDate.isBefore(today)
                ? JobPostingStatus.CLOSE : JobPostingStatus.OPEN;
        LocalDate regDate = parseDate(row.regDt());

        JobPosting posting = JobPosting.builder()
                .title(truncate(row.title().strip(), 200))
                .employmentType(employmentType(row.empTpCd()))
                .careerType(careerType(row.enterTpCd()))
                .regionCode(truncate(row.regionCd().strip(), 30))
                .regionName(truncate(trimToNull(row.region()), 50))
                // TODO: jobGroup 판정 규칙 확정 후 반영 (그 전까지 모든 공고 null)
                .jobGroup(null)
                .workplaceAddress(truncate(workplaceAddress(row), 255))
                .description(trimToNull(row.jobCont()))
                .publishedAt(regDate == null ? null : regDate.atStartOfDay())
                .deadlineType(deadlineType(closeDt, closeDate))
                .closesAt(closesAt)
                .status(status)
                .source(SOURCE)
                .externalId(externalId(row))
                .educationRequirement(truncate(trimToNull(row.minEdubg()), 50))
                .externalJobCode(truncate(trimToNull(row.jobsCd()), 10))
                .workSchedule(truncate(trimToNull(row.workdayWorkhrCont()), 255))
                .benefits(joinLines(row.fourIns(), row.retirepay(), row.etcWelfare()))
                .applicationMethod(joinLines(row.selMthd(), row.rcptMthd(), row.submitDoc()))
                .build();

        JobPostingPay pay = pay(row.salTpCd(), row.salTpNm());
        if (pay != null) {
            posting.addPay(pay);
        }
        addRequirements(posting, row);

        return new JobPostingSyncItem(posting, truncate(row.company().strip(), 200),
                truncate(trimToNull(row.industryName()), 100));
    }

    // 고유 ID가 없어 기업명·제목·등록일·근무지로 식별. 제목이 바뀌면 다른 공고로 취급 (MVP에서 감수)
    static String externalId(SeoulJobRow row) {
        return externalId(row.company(), row.title(), row.regDt(), row.workRegion());
    }

    static String externalId(String company, String title, String regDt, String workRegion) {
        String key = String.join("\u001F",
                nullToEmpty(company).strip(), nullToEmpty(title).strip(),
                nullToEmpty(regDt).strip(), nullToEmpty(workRegion).strip());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // 10·11: 기간의 정함이 없는 근로계약(시간선택제 포함), 20·21: 기간의 정함이 있는 근로계약
    private EmploymentType employmentType(String code) {
        return code != null && code.startsWith("1") ? EmploymentType.FULL_TIME : EmploymentType.CONTRACT;
    }

    private CareerType careerType(String code) {
        if ("N".equals(code)) {
            return CareerType.NEW;
        }
        if ("E".equals(code)) {
            return CareerType.EXPERIENCED;
        }
        return CareerType.ANY;
    }

    // "(02076)  서울특별시 ..." 앞의 우편번호 제거. 비어 있으면 회사 주소 사용
    private String workplaceAddress(SeoulJobRow row) {
        String address = trimToNull(row.workRegion());
        if (address != null) {
            return POSTAL_PREFIX.matcher(address).replaceFirst("").strip();
        }
        String corpAddr = trimToNull(row.corpAddr());
        return corpAddr != null ? corpAddr : nullToEmpty(row.regionCd()).strip();
    }

    // "채용시까지  26-11-15" → UNTIL_FILLED, "26-11-13" → FIXED, 날짜 없음 → ALWAYS
    private DeadlineType deadlineType(String closeDt, LocalDate closeDate) {
        if (closeDt != null && closeDt.contains(UNTIL_FILLED)) {
            return DeadlineType.UNTIL_FILLED;
        }
        return closeDate != null ? DeadlineType.FIXED : DeadlineType.ALWAYS;
    }

    // "월급2,600,000원 이상 ~ 4,000,000원 이하, 면접 후 재조정 가능"
    private JobPostingPay pay(String code, String text) {
        PayPeriod period = switch (nullToEmpty(code)) {
            case "Y" -> PayPeriod.YEARLY;
            case "M" -> PayPeriod.MONTHLY;
            case "D" -> PayPeriod.DAILY;
            case "H" -> PayPeriod.HOURLY;
            default -> null;
        };
        if (period == null) {
            return null;
        }

        String description = trimToNull(text);
        if (description != null && description.endsWith(",")) {
            description = description.substring(0, description.length() - 1).strip();
        }
        Long min = parseAmount(AMOUNT_MIN, description);
        Long max = parseAmount(AMOUNT_MAX, description);

        return JobPostingPay.builder()
                .stage(PayStage.CURRENT)
                .payPolicy(min != null || max != null ? PayPolicy.SPECIFIED : PayPolicy.NEGOTIABLE)
                .payPeriod(period)
                .amountMin(min)
                .amountMax(max)
                .description(truncate(description, 255))
                .build();
    }

    // 학력(학력무관 제외)·전공·자격증·컴퓨터활용능력은 필수, 우대조건은 우대. 빈 값은 건너뜀
    private void addRequirements(JobPosting posting, SeoulJobRow row) {
        short order = 1;
        if (!NO_EDUCATION_REQUIRED.equals(row.minEdubgIcd())) {
            order = addRequirement(posting, RequirementPriority.REQUIRED, RequirementType.DEGREE, row.minEdubg(), order);
        }
        order = addRequirement(posting, RequirementPriority.REQUIRED, RequirementType.MAJOR, row.major(), order);
        order = addRequirement(posting, RequirementPriority.REQUIRED, RequirementType.CERTIFICATE, row.certificate(), order);
        order = addRequirement(posting, RequirementPriority.REQUIRED, RequirementType.SKILL, row.compAbl(), order);
        addRequirement(posting, RequirementPriority.PREFERRED, RequirementType.ETC, row.pfCond(), order);
    }

    private short addRequirement(JobPosting posting, RequirementPriority priority, RequirementType type,
                                 String content, short order) {
        String value = trimToNull(content);
        if (value == null) {
            return order;
        }
        posting.addRequirement(JobPostingRequirement.builder()
                .priority(priority)
                .type(type)
                .content(truncate(value, 500))
                .sortOrder(order)
                .build());
        return (short) (order + 1);
    }

    private static Long parseAmount(Pattern pattern, String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Long.parseLong(matcher.group(1).replace(",", "")) : null;
    }

    private static LocalDate parseDate(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.parse(matcher.group(), DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String joinLines(String... values) {
        String joined = Stream.of(values)
                .map(SeoulJobPostingMapper::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
        return joined.isEmpty() ? null : joined;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
