
package com.lifelog.domain.member.repository;

import com.lifelog.domain.member.entity.MemberIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberIdentityRepository extends JpaRepository<MemberIdentity, Long> {

    Optional<MemberIdentity> findByProviderAndProviderSubject(
            String provider,
            String providerSubject
    );
}
