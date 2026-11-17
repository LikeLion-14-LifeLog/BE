package com.lifelog.domain.specprofile.dto;

import com.lifelog.domain.specprofile.enums.CompanyGroupScale;

/** 현재·목표 스냅샷과 완료된 시뮬레이션의 조건·분석 결과. */
public record SpecProfileSimulationContext(
        SpecProfileAnalyzationSnapshot base,
        SpecProfileAnalyzationSnapshot simulated,
        CompanyGroupScale companyGroupScale,
        SpecProfileAnnualSalaryAnalyzationResDto baseAnalysis,
        SpecProfileAnnualSalaryAnalyzationResDto simulatedAnalysis,
        SpecProfileSimulationResDto response
) {
    public SpecProfileSimulationContext(SpecProfileAnalyzationSnapshot base,
                                        SpecProfileAnalyzationSnapshot simulated) {
        this(base, simulated, null, null, null, null);
    }
}
