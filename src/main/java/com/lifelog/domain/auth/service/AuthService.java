
package com.lifelog.domain.auth.service;

import com.lifelog.domain.auth.dto.LoginRequest;
import com.lifelog.domain.auth.dto.SignupRequest;
import com.lifelog.domain.auth.exception.InvalidCredentialsException;
import com.lifelog.domain.member.entity.Member;
import com.lifelog.domain.member.entity.MemberIdentity;
import com.lifelog.domain.member.repository.MemberIdentityRepository;
import com.lifelog.domain.member.repository.MemberRepository;
import com.lifelog.domain.specprofile.service.SpecProfileService;
import com.lifelog.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        if (memberRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        String encodedPassword =
                passwordEncoder.encode(request.password());

        Member member = memberRepository.save(
                new Member(request.email())
        );

        MemberIdentity identity = new MemberIdentity(
                member,
                encodedPassword
        );

        memberIdentityRepository.save(identity);

        specProfileService.createCurrentProfile(member);

        log.info("[AuthService] signup() - END | memberId: {}", member.getId());
        return member.getId();
    }


    @Transactional(readOnly = true)
    public String login(LoginRequest request) {

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
        return jwtProvider.createAccessToken(member.getId());
    }
}
