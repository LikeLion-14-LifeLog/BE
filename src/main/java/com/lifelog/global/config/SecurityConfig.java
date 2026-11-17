
package com.lifelog.global.config;

import com.lifelog.global.security.JwtAuthenticationEntryPoint;
import com.lifelog.global.security.JwtAuthenticationFilter;
import com.lifelog.global.security.JwtAccessDeniedHandler;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {

        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())

                // 세션 대신 JWT 사용
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // 인증 실패 시 401 반환
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(
                                jwtAuthenticationEntryPoint
                        ).accessDeniedHandler(jwtAccessDeniedHandler)
                )

                // API 접근 권한 설정
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(request -> request.getDispatcherType() == DispatcherType.ASYNC
                                && request.getRequestURI().matches("/api/spec-profile/analyzation/runs/[0-9]+/events")).permitAll()

                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                                // 인증 없이 접근 가능한 API
                                .requestMatchers(
                                        "/api/auth/signup",
                                        "/api/auth/login",
                                        "/health",
                                        "/api/v1/statistics/exploratory-first-job-change"
                                ).permitAll()


                                // 커리어 패스 조회 API는 인증 없이 접근 가능
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/api/v1/career-paths",
                                        "/api/v1/career-paths/*",
                                        "/api/v1/career-paths/exploratory/*",
                                        "/api/v1/career-paths/yearly",
                                        "/api/v1/career-paths/yearly/graph",
                                        "/api/v1/career-paths/yearly/routes",
                                        "/api/v1/career-paths/yearly/routes/*/*"
                                ).permitAll()

                                // Swagger 허용 (개발용)
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // 관리자만 허용되는 엔드포인트들
                        .requestMatchers(
                                "/api/admin/certifications/init",
                                "/api/admin/majors/init",
                                "/api/admin/jobs/init",
                                "/api/admin/language-exams/init",
                                "/api/admin/job-postings/sync",
                                "/api/admin/job-postings/sync/status"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/career-paths/exploratory/graph"
                        ).permitAll()

                        // 나머지 API는 인증 필요
                        .anyRequest().authenticated()
                )

                // JWT 인증 필터 등록
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
