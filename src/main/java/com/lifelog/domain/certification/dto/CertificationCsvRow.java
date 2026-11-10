package com.lifelog.domain.certification.dto;
import com.lifelog.domain.certification.enums.*;
public record CertificationCsvRow(String name, String code, CertificationMainCategory mainCategory,
                                  CertificationType type, String authorizedInstitutionName) {}
