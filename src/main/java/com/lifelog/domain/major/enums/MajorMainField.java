package com.lifelog.domain.major.enums;

public enum MajorMainField {

    HUMANITIES("인문계열"),
    SOCIAL_SCIENCES("사회계열"),
    NATURAL_SCIENCES("자연계열"),
    ENGINEERING("공학계열"),
    MEDICINE_PHARMACY("의약학계열"),
    EDUCATION("교육계열"),
    ARTS_PHYSICAL_EDUCATION("예체능계열");

    private final String korean;

    MajorMainField(String korean) {
        this.korean = korean;
    }

    public String getKorean() {
        return korean;
    }
}
