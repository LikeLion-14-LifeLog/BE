package com.lifelog.domain.jobposting;

import com.lifelog.domain.jobposting.dto.SeoulJobRow;

public final class JobPostingFixtures {

    private JobPostingFixtures() {
    }

    // 실제 응답 형식을 따른 기본 공고 (마감일은 먼 미래)
    public static SeoulJobRow.SeoulJobRowBuilder row(String title) {
        return SeoulJobRow.builder()
                .company("가락문화자동차공업사")
                .title(title)
                .regDt("26-09-14")
                .closeDt("채용시까지  99-12-31")
                .region("서울 송파구")
                .regionCd("11710")
                .workRegion("(05824)  서울특별시 송파구 중대로 178 (가락동)")
                .corpAddr("05824 서울특별시 송파구 중대로 178 (가락동)")
                .industryName("자동차 재제조 부품 제조업")
                .jobsCd("133200")
                .jobCont("자동차종합검사 업무")
                .empTpCd("10")
                .enterTpCd("E")
                .salTpCd("M")
                .salTpNm("월급2,600,000원 이상 ~ 4,000,000원 이하, 면접 후 재조정 가능")
                .minEdubg("학력무관")
                .minEdubgIcd("00")
                .major("")
                .certificate("자동차정비산업기사")
                .compAbl("")
                .pfCond("")
                .workdayWorkhrCont("평일 : (근무시간) (오전) 8시 30분 ~ (오후) 6시 00분, 주 6일 근무")
                .fourIns("국민연금 고용보험 산재보험 의료보험")
                .retirepay("퇴직금")
                .etcWelfare("")
                .selMthd("서류,면접")
                .rcptMthd("방문,고용24")
                .submitDoc("이력서");
    }
}
