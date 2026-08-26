package com.hwandevblog.invmgmt;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;

/**
 * 컨테이너 없이 로컬에서 애플리케이션을 실행하기 위한 진입점이다.
 * 테스트 소스에 두어 Embedded PostgreSQL 의존성이 운영 JAR에 포함되지 않도록 한다.
 */
public final class LocalApplication {

    private static EmbeddedPostgres postgres;

    private LocalApplication() {
    }

    public static void main(String[] args) throws IOException {
        postgres = EmbeddedPostgres.builder()
                // 실행 중인 PostgreSQL 서버 메시지를 영문으로 출력해 Windows 인코딩 충돌을 피한다.
                .setLocaleConfig("lc-messages", "C")
                .start();
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "postgres");
        InvMgmtApplication.main(args);
    }
}
