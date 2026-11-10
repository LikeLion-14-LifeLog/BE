package com.lifelog.domain.certification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CertificationMainCategory {
    COMMON("공통"),
    BUSINESS_ACCOUNTING_ADMINISTRATION("경영·회계·사무"),
    FINANCE_INSURANCE_REAL_ESTATE("금융·보험·부동산"),
    EDUCATION_SCIENCE_RESEARCH("교육 및 자연과학·사회과학연구"),
    LAW_POLICE_FIRE_CORRECTIONS("법률·경찰·소방·교도"),
    HEALTHCARE_MEDICAL("보건·의료"),
    SOCIAL_WELFARE_RELIGION("사회복지 및 종교"),
    CULTURE_ART_DESIGN_BROADCASTING("문화·예술·디자인·방송"),
    DRIVING_TRANSPORTATION("운전·운송"),
    SALES_PERSONAL_SERVICES("영업·판매 및 대인서비스"),
    ENVIRONMENT_SAFETY_MANAGEMENT("환경·안전관리"),
    HOSPITALITY_TRAVEL_LEISURE_SPORTS("숙박·여행·오락·스포츠"),
    FOOD_SERVICE("음식서비스"),
    CONSTRUCTION("건설"),
    MACHINING_MAINTENANCE("기계가공·정비"),
    MATERIALS("재료"),
    CHEMICAL_ENERGY("화학·에너지"),
    TEXTILES_APPAREL("섬유 및 의복"),
    ELECTRICAL_ELECTRONICS("전기·전자"),
    INFORMATION_COMMUNICATIONS("정보통신"),
    FOOD_PROCESSING("식품가공"),
    PRINTING_WOOD_FURNITURE_FINE_PROCESSING("인쇄·목재·가구·세가공"),
    AGRICULTURE_FORESTRY_MARITIME_FISHERIES("농림·해양·수산");

    private final String korean;
}
