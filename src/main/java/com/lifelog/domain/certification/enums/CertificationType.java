package com.lifelog.domain.certification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CertificationType {
    NATIONAL_PROFESSIONAL("국가전문자격증"),
    NATIONAL_TECHNICAL("국가기술자격증"),
    INTERNATIONAL("국제자격증"),
    PRIVATE("민간자격증");

    private final String korean;
}
