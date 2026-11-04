package com.lifelog.global.util;

import com.lifelog.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {

  @GetMapping("/health")
  public ApiResponse<String> health() {
    return ApiResponse.success("ok");
  }
}