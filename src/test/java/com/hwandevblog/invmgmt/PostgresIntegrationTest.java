package com.hwandevblog.invmgmt;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

/**
 * 통합 테스트가 H2 대체 구현이 아닌 실제 PostgreSQL 동작을 검증하도록 공통 DB를 제공한다.
 * 한 테스트 JVM에서 하나의 인스턴스를 공유해 반복적인 초기화 비용을 줄인다.
 */
public abstract class PostgresIntegrationTest {

    private static final EmbeddedPostgres POSTGRES = startPostgres();

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        // Spring 컨텍스트가 DataSource를 만들기 전에 임시 PostgreSQL 접속 정보를 주입한다.
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    private static EmbeddedPostgres startPostgres() {
        try {
            return EmbeddedPostgres.builder()
                    // 서버 로그는 영문으로 고정하되 데이터베이스의 다른 로케일 설정은 유지한다.
                    .setLocaleConfig("lc-messages", "C")
                    .start();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start embedded PostgreSQL", exception);
        }
    }
}
