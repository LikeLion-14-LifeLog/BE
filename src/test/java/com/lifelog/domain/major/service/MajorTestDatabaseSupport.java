package com.lifelog.domain.major.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

final class MajorTestDatabaseSupport {

    private MajorTestDatabaseSupport() {
    }

    static synchronized void initialize(DataSource dataSource) throws Exception {
        /* 공유 H2 DB에 초기화가 완료되어 있으면 기존 스키마를 그대로 사용한다. */
        try (var connection = dataSource.getConnection();
             var tables = connection.getMetaData().getTables(null, "PUBLIC", "MAJOR_INIT_LOCK", null)) {
            if (tables.next()) {
                return;
            }
        }

        /* 전공 테이블은 공통 Hibernate 설정으로 생성하고, 엔티티가 없는 잠금 테이블만 준비한다. */
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V20261109_4__add_major_init_lock.sql")
        ).execute(dataSource);
    }
}
