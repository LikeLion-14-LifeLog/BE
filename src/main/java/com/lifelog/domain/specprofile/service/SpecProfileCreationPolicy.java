package com.lifelog.domain.specprofile.service;

import com.lifelog.domain.specprofile.enums.SpecProfileType;
import com.lifelog.domain.specprofile.exception.SpecProfileErrorCode;
import com.lifelog.global.exception.CustomException;
import org.springframework.stereotype.Component;

@Component
public class SpecProfileCreationPolicy {
    /** 회원 잠금 상태에서 유형별 프로필 생성 한도를 검증한다. */
    public void validate(SpecProfileType type, long existingCount) {
        int limit = type == SpecProfileType.CURRENT ? 1 : 3;
        if (existingCount >= limit) {
            throw new CustomException(SpecProfileErrorCode.PROFILE_LIMIT);
        }
    }
}

