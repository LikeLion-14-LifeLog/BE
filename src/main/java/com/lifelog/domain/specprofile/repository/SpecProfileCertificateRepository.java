package com.lifelog.domain.specprofile.repository;

import com.lifelog.domain.specprofile.entity.SpecProfileCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;

public interface SpecProfileCertificateRepository extends JpaRepository<SpecProfileCertificate, Long> {
    /** 지정한 프로필의 항목만 조회한다. */
    @EntityGraph(attributePaths = "certificate")
    List<SpecProfileCertificate> findAllBySpecProfileId(Long specProfileId);
}

