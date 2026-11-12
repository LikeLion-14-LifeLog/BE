package com.lifelog.domain.jobposting.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmploymentType {
    FULL_TIME("정규직"),
    CONTRACT("계약직"),
    INTERN("인턴");

    private final String label;
}
