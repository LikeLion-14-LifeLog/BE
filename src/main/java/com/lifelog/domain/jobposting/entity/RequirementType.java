package com.lifelog.domain.jobposting.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RequirementType {
    MAJOR("전공"),
    DEGREE("학력"),
    SKILL("기술"),
    EXPERIENCE("경력"),
    CERTIFICATE("자격증"),
    LANGUAGE("어학"),
    ETC("기타");

    private final String label;
}
