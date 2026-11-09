package com.lifelog.domain.major.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

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

        /* 최초 한 번만 실제 테이블 정의와 전공 마이그레이션을 적용한다. */
        String initialSchema = new ClassPathResource("db/migration/V1__init.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        int start = initialSchema.indexOf("CREATE TABLE major");
        String majorTable = initialSchema.substring(start, initialSchema.indexOf(");", start) + 2);
        new ResourceDatabasePopulator(
                new ByteArrayResource(majorTable.getBytes(StandardCharsets.UTF_8)),
                new ClassPathResource("db/migration/V20261109_1__rename_major_degree_process.sql"),
                new ClassPathResource("db/migration/V20261109_2__rename_major_field.sql"),
                new ClassPathResource("db/migration/V20261109_3__update_major_schema.sql"),
                new ClassPathResource("db/migration/V20261109_4__add_major_init_lock.sql")
        ).execute(dataSource);
    }
}
