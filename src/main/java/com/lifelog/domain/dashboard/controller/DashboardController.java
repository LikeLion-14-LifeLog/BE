package com.lifelog.domain.dashboard.controller;

import com.lifelog.domain.dashboard.dto.DashboardResDto;
import com.lifelog.domain.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardController implements DashboardControllerDocs {
    private final DashboardService service;

    @Override
    public DashboardResDto getDashboard(Long memberId) {
        return service.getDashboard(memberId);
    }
}
