package com.lifelog.domain.major.enums;

public enum MajorMiddleField {

    NURSING("간호"),
    ARCHITECTURE("건축"),
    BUSINESS_ECONOMICS("경영ㆍ경제"),
    GENERAL_EDUCATION("교육일반"),
    TRANSPORTATION("교통ㆍ운송"),
    MECHANICAL_METALLURGY("기계ㆍ금속"),
    OTHER("기타"),
    AGRICULTURE_FORESTRY_FISHERIES("농림ㆍ수산"),
    DESIGN("디자인"),
    DANCE_PHYSICAL_EDUCATION("무용ㆍ체육"),
    FINE_ARTS_SCULPTURE("미술ㆍ조형"),
    LAW("법률"),
    SOCIAL_SCIENCES("사회과학"),
    INDUSTRIAL_STUDIES("산업"),
    BIOLOGY_CHEMISTRY_ENVIRONMENT("생물ㆍ화학ㆍ환경"),
    HUMAN_ECOLOGY("생활과학"),
    MATERIALS("소재ㆍ재료"),
    MATHEMATICS_PHYSICS_ASTRONOMY_GEOGRAPHY("수학ㆍ물리ㆍ천문ㆍ지리"),
    PHARMACY("약학"),
    LANGUAGES_LITERATURE("언어ㆍ문학"),
    THEATER_FILM("연극ㆍ영화"),
    EARLY_CHILDHOOD_EDUCATION("유아교육"),
    MUSIC("음악"),
    APPLIED_ARTS("응용예술"),
    MEDICAL_SCIENCES("의료"),
    HUMANITIES("인문과학"),
    ELECTRICAL_ELECTRONICS("전기ㆍ전자"),
    PRECISION_ENGINEERING_ENERGY("정밀ㆍ에너지"),
    SECONDARY_EDUCATION("중등교육"),
    ELEMENTARY_EDUCATION("초등교육"),
    THERAPY_HEALTH("치료ㆍ보건"),
    COMPUTER_COMMUNICATIONS("컴퓨터ㆍ통신"),
    CIVIL_ENGINEERING_URBAN_PLANNING("토목ㆍ도시"),
    SPECIAL_EDUCATION("특수교육"),
    CHEMICAL_ENGINEERING("화공");

    private final String korean;

    MajorMiddleField(String korean) {
        this.korean = korean;
    }

    public String getKorean() {
        return korean;
    }
}
