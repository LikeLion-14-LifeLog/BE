package com.lifelog.domain.residentialarea.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record ResidentialAreaListResDto(List<ResidentialAreaResDto> residentialAreas) {
    public static ResidentialAreaListResDto create(List<ResidentialAreaResDto> residentialAreas) {
        return ResidentialAreaListResDto.builder().residentialAreas(residentialAreas).build();
    }
}
