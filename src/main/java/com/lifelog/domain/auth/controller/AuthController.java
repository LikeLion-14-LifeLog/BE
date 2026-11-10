
package com.lifelog.domain.auth.controller;

import com.lifelog.domain.auth.dto.LoginRequest;
import com.lifelog.domain.auth.dto.SignupRequest;
import com.lifelog.domain.auth.service.AuthService;
import com.lifelog.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 회원가입 API
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Map<String, Object>>> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        Long memberId = authService.signup(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        Map.of("memberId", memberId)
                ));
    }

    // 로그인 API
    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        String accessToken = authService.login(request);

        return ApiResponse.success(
                Map.of(
                        "accessToken", accessToken,
                        "tokenType", "Bearer"
                )
        );
    }
}
