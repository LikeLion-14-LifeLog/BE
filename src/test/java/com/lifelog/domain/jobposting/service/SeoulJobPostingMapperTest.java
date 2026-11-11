package com.lifelog.domain.jobposting.service;

import com.lifelog.domain.jobposting.entity.CareerType;
import com.lifelog.domain.jobposting.entity.DeadlineType;
import com.lifelog.domain.jobposting.entity.EmploymentType;
import com.lifelog.domain.jobposting.entity.JobPosting;
import com.lifelog.domain.jobposting.entity.JobPostingPay;
import com.lifelog.domain.jobposting.entity.JobPostingStatus;
import com.lifelog.domain.jobposting.entity.PayPeriod;
import com.lifelog.domain.jobposting.entity.PayPolicy;
import com.lifelog.domain.jobposting.entity.RequirementPriority;
import com.lifelog.domain.jobposting.entity.RequirementType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.lifelog.domain.jobposting.JobPostingFixtures.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SeoulJobPostingMapperTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    private final SeoulJobPostingMapper mapper = new SeoulJobPostingMapper();

    @Test
    void 기본_필드_변환() {
        JobPostingSyncItem item = mapper.toItem(row("자동차 종합검사원 채용").build(), TODAY);
        JobPosting posting = item.posting();

        assertThat(item.companyName()).isEqualTo("가락문화자동차공업사");
        assertThat(item.industryName()).isEqualTo("자동차 재제조 부품 제조업");
        assertThat(posting.getSource()).isEqualTo("SEOUL_JOB");
        assertThat(posting.getEmploymentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(posting.getCareerType()).isEqualTo(CareerType.EXPERIENCED);
        assertThat(posting.getRegionCode()).isEqualTo("11710");
        assertThat(posting.getWorkplaceAddress()).isEqualTo("서울특별시 송파구 중대로 178 (가락동)");
        assertThat(posting.getPublishedAt()).isEqualTo(LocalDateTime.of(2026, 9, 14, 0, 0));
        assertThat(posting.getExternalJobCode()).isEqualTo("817100");
        assertThat(posting.getEducationRequirement()).isEqualTo("학력무관");
        assertThat(posting.getApplyUrl()).isNull();
    }

    @Test
    void 복리후생과_지원방법은_줄바꿈으로_합치고_빈_값은_건너뜀() {
        JobPosting posting = mapper.toItem(row("제목").etcWelfare("  ").selMthd("").build(), TODAY).posting();

        assertThat(posting.getBenefits()).isEqualTo("국민연금 고용보험 산재보험 의료보험\n퇴직금");
        assertThat(posting.getApplicationMethod()).isEqualTo("방문,고용24\n이력서");
    }

    @Test
    void 모든_값이_비어_있으면_null() {
        JobPosting posting = mapper.toItem(row("제목").fourIns("").retirepay("").etcWelfare("").build(), TODAY).posting();

        assertThat(posting.getBenefits()).isNull();
    }

    @Test
    void 채용시까지_마감일은_UNTIL_FILLED이고_그날_끝까지_유효() {
        JobPosting posting = mapper.toItem(row("제목").closeDt("채용시까지  26-11-15").build(), TODAY).posting();

        assertThat(posting.getDeadlineType()).isEqualTo(DeadlineType.UNTIL_FILLED);
        assertThat(posting.getClosesAt()).isEqualTo(LocalDateTime.of(2026, 11, 15, 23, 59, 59));
        assertThat(posting.getStatus()).isEqualTo(JobPostingStatus.OPEN);
    }

    @Test
    void 날짜만_있으면_FIXED_오늘_마감은_OPEN() {
        JobPosting posting = mapper.toItem(row("제목").closeDt("26-11-11").build(), TODAY).posting();

        assertThat(posting.getDeadlineType()).isEqualTo(DeadlineType.FIXED);
        assertThat(posting.getStatus()).isEqualTo(JobPostingStatus.OPEN);
    }

    @Test
    void 마감일이_지났으면_CLOSE() {
        JobPosting posting = mapper.toItem(row("제목").closeDt("26-11-10").build(), TODAY).posting();

        assertThat(posting.getStatus()).isEqualTo(JobPostingStatus.CLOSE);
    }

    @Test
    void 마감일이_없으면_ALWAYS() {
        JobPosting posting = mapper.toItem(row("제목").closeDt("").build(), TODAY).posting();

        assertThat(posting.getDeadlineType()).isEqualTo(DeadlineType.ALWAYS);
        assertThat(posting.getClosesAt()).isNull();
    }

    @Test
    void 급여_범위() {
        JobPostingPay pay = mapper.toItem(row("제목").build(), TODAY).posting().getPays().getFirst();

        assertThat(pay.getPayPeriod()).isEqualTo(PayPeriod.MONTHLY);
        assertThat(pay.getPayPolicy()).isEqualTo(PayPolicy.SPECIFIED);
        assertThat(pay.getAmountMin()).isEqualTo(2_600_000L);
        assertThat(pay.getAmountMax()).isEqualTo(4_000_000L);
        assertThat(pay.getDescription()).isEqualTo("월급2,600,000원 이상 ~ 4,000,000원 이하, 면접 후 재조정 가능");
    }

    @Test
    void 급여_하한만_있으면_상한은_null이고_끝_쉼표_제거() {
        JobPostingPay pay = mapper.toItem(row("제목").salTpCd("H").salTpNm("시급10,320원 이상,").build(), TODAY)
                .posting().getPays().getFirst();

        assertThat(pay.getPayPeriod()).isEqualTo(PayPeriod.HOURLY);
        assertThat(pay.getAmountMin()).isEqualTo(10_320L);
        assertThat(pay.getAmountMax()).isNull();
        assertThat(pay.getDescription()).isEqualTo("시급10,320원 이상");
    }

    @Test
    void 일급은_DAILY() {
        JobPostingPay pay = mapper.toItem(row("제목").salTpCd("D").salTpNm("일급100,000원 이상,").build(), TODAY)
                .posting().getPays().getFirst();

        assertThat(pay.getPayPeriod()).isEqualTo(PayPeriod.DAILY);
    }

    @Test
    void 기간제는_CONTRACT_신입은_NEW() {
        JobPosting posting = mapper.toItem(row("제목").empTpCd("20").enterTpCd("N").build(), TODAY).posting();

        assertThat(posting.getEmploymentType()).isEqualTo(EmploymentType.CONTRACT);
        assertThat(posting.getCareerType()).isEqualTo(CareerType.NEW);
    }

    @Test
    void 자격요건은_빈_값과_학력무관을_건너뛰고_순서대로_저장() {
        JobPosting posting = mapper.toItem(row("제목")
                .minEdubg("대졸(4년)").minEdubgIcd("05")
                .pfCond("(준)고령자(50세이상)")
                .build(), TODAY).posting();

        assertThat(posting.getRequirements())
                .extracting("priority", "type", "content", "sortOrder")
                .containsExactly(
                        tuple(RequirementPriority.REQUIRED, RequirementType.DEGREE, "대졸(4년)", (short) 1),
                        tuple(RequirementPriority.REQUIRED, RequirementType.CERTIFICATE, "자동차정비산업기사", (short) 2),
                        tuple(RequirementPriority.PREFERRED, RequirementType.ETC, "(준)고령자(50세이상)", (short) 3));

        JobPosting noDegree = mapper.toItem(row("제목").build(), TODAY).posting();
        assertThat(noDegree.getRequirements()).extracting("type").containsExactly(RequirementType.CERTIFICATE);
    }

    @Test
    void 외부ID는_같은_공고면_같고_제목이_다르면_다름() {
        String id = mapper.toItem(row("제목").build(), TODAY).externalId();

        assertThat(id).hasSize(64);
        assertThat(mapper.toItem(row("제목").jobCont("내용 변경").build(), TODAY).externalId()).isEqualTo(id);
        assertThat(mapper.toItem(row("다른 제목").build(), TODAY).externalId()).isNotEqualTo(id);
    }
}
