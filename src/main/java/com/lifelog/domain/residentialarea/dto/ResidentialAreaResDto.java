package com.lifelog.domain.residentialarea.dto;

import lombok.Builder;

@Builder
public record ResidentialAreaResDto(String code, String name) {
    public static ResidentialAreaResDto create(String code, String name) {
        return ResidentialAreaResDto.builder().code(code).name(name).build();
    }
}
