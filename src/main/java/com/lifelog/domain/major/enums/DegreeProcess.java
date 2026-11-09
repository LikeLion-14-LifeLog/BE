package com.lifelog.domain.major.enums;

public enum DegreeProcess {

    HIGH_SCHOOL("고등학교"),
    COLLEGE("전문학사"),
    UNIVERSITY("학사"),
    GRADUATE_SCHOOL("석박사");

    private final String korean;

    DegreeProcess(String korean) {
        this.korean = korean;
    }

    public String getKorean() {
        return korean;
    }
}
