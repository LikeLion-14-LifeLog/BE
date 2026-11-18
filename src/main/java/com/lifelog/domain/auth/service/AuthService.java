package com.lifelog.domain.auth.service;

import com.lifelog.domain.auth.dto.LoginRequest;
import com.lifelog.domain.auth.dto.SignupRequest;
import com.lifelog.domain.auth.exception.AuthErrorCode;
import com.lifelog.domain.auth.exception.InvalidCredentialsException;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.entity.MemberIdentity;
import com.lifelog.domain.member.repository.MemberIdentityRepository;
import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.domain.specprofile.service.SpecProfileService;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AuthService {

    private final MemberRepository memberRepository;
    private final MemberIdentityRepository memberIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final SpecProfileService specProfileService;

    /** 회원, 인증 정보, CURRENT 프로필을 하나의 트랜잭션으로 생성한다. */
    @Transactional
    public Long signup(SignupRequest request) {

        log.info("[AuthService] signup() - START");

        // 1. 이메일 중복 검사
        if (memberRepository.existsByEmail(request.email())) {
            throw new CustomException(AuthErrorCode.DUPLICATE_EMAIL);
        }

        // 2. 비밀번호 암호화
        String encodedPassword =
                passwordEncoder.encode(request.password());

        Member member;

        try {
            // 3. 회원 및 닉네임 저장
            member = memberRepository.saveAndFlush(
                    new Member(request.email(), request.nickname().strip())
            );

            // 4. 인증 정보 저장
            MemberIdentity identity = new MemberIdentity(
                    member,
                    encodedPassword
            );

            memberIdentityRepository.saveAndFlush(identity);

        } catch (DataIntegrityViolationException e) {

            // 동시 가입으로 발생한 이메일 중복 처리
            if (isDuplicateEmailViolation(e)) {
                throw new CustomException(
                        AuthErrorCode.DUPLICATE_EMAIL
                );
            }

            // 이메일 중복이 아닌 다른 DB 오류는 그대로 전달
            throw e;
        }

        // 5. CURRENT 프로필 자동 생성
        specProfileService.createCurrentProfile(member);

        log.info("[AuthService] signup() - END | memberId: {}",
                member.getId());

        return member.getId();
    }

    private boolean isDuplicateEmailViolation(Throwable exception) {

        Throwable cause = exception;

        while (cause != null) {

            if (cause instanceof ConstraintViolationException violation) {

                String constraintName = violation.getConstraintName();

                if ("23505".equals(violation.getSQLState())
                        && (
                        "member_email_key".equalsIgnoreCase(constraintName)
                                || "member_identity_provider_provider_subject_key"
                                .equalsIgnoreCase(constraintName)
                )) {
                    return true;
                }
            }

            cause = cause.getCause();
        }

        return false;
    }

    @Transactional(readOnly = true)
    public Map<String, String> login(LoginRequest request) {

        // 1. 이메일로 회원 조회
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        // 2. LOCAL 로그인 정보 조회
        MemberIdentity identity = memberIdentityRepository
                .findByProviderAndProviderSubject(
                        "LOCAL",
                        member.getEmail()
                )
                .orElseThrow(InvalidCredentialsException::new);

        // 3. 비밀번호 확인
        if (identity.getPasswordHash() == null ||
                !passwordEncoder.matches(
                        request.password(),
                        identity.getPasswordHash()
                )) {
            throw new InvalidCredentialsException();
        }

        // 4. 회원 상태 확인
        if (!"ACTIVE".equals(member.getStatus())) {
            throw new IllegalStateException(
                    "로그인할 수 없는 회원입니다."
            );
        }

        // 5. JWT Access Token 발급
        String accessToken =
                jwtProvider.createAccessToken(member.getId());

        // 6. 로그인 응답 반환
        return Map.of(
                "accessToken", accessToken,
                "tokenType", "Bearer",
                "nickname", member.getNickname()
        );
    }
}