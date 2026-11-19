package com.lifelog.domain.employeecard.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmployeeCardGender {
    MALE("남성"),
    FEMALE("여성");

    // 프롬프트에 넣는 한글 표기
    private final String label;
}
