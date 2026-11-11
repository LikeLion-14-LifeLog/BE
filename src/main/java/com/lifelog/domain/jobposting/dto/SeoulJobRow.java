package com.lifelog.domain.jobposting.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

// 서울 일자리포털 채용정보(recMntList) 한 건. 사용하는 필드만 매핑
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeoulJobRow(
        @JsonProperty("COMPANY") String company,
        @JsonProperty("TITLE") String title,
        @JsonProperty("REG_DT") String regDt,
        @JsonProperty("CLOSE_DT") String closeDt,
        @JsonProperty("REGION_CD") String regionCd,
        @JsonProperty("WORK_REGION") String workRegion,
        @JsonProperty("CORP_ADDR") String corpAddr,
        @JsonProperty("IND_TP_CD_NM") String industryName,
        @JsonProperty("JOBS_CD") String jobsCd,
        @JsonProperty("JOB_CONT") String jobCont,
        @JsonProperty("EMP_TP_CD") String empTpCd,
        @JsonProperty("ENTER_TP_CD") String enterTpCd,
        @JsonProperty("SAL_TP_CD") String salTpCd,
        @JsonProperty("SAL_TP_NM") String salTpNm,
        @JsonProperty("MIN_EDUBG") String minEdubg,
        @JsonProperty("MIN_EDUBG_ICD") String minEdubgIcd,
        @JsonProperty("MAJOR") String major,
        @JsonProperty("CERTIFICATE") String certificate,
        @JsonProperty("COMP_ABL") String compAbl,
        @JsonProperty("PF_COND") String pfCond,
        @JsonProperty("WORKDAY_WORKHR_CONT") String workdayWorkhrCont,
        @JsonProperty("FOUR_INS") String fourIns,
        @JsonProperty("RETIREPAY") String retirepay,
        @JsonProperty("ETC_WELFARE") String etcWelfare,
        @JsonProperty("SEL_MTHD") String selMthd,
        @JsonProperty("RCPT_MTHD") String rcptMthd,
        @JsonProperty("SUBMIT_DOC") String submitDoc
) {
}
