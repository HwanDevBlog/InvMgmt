package com.hwandevblog.invmgmt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * 고정 포트와 데이터 디렉터리로 로컬 애플리케이션을 실행한다.
 * 로컬 전용 PostgreSQL 의존성은 테스트 소스에 두어 운영 JAR에서 제외한다.
 */
public final class LocalApplication {

    private static final Logger log = LoggerFactory.getLogger(LocalApplication.class);

    private LocalApplication() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(System.getProperty("invmgmt.local.root", ".local")).toAbsolutePath().normalize();
        var database = LocalPostgres.start(root, 15432);
        Thread shutdown = new Thread(() -> {
            try { database.close(); } catch (Exception exception) { log.error("Local database shutdown failed", exception); }
        }, "invmgmt-local-database-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdown);
        try {
            System.setProperty("spring.datasource.url", database.jdbcUrl());
            System.setProperty("spring.datasource.username", "postgres");
            System.setProperty("spring.datasource.password", "postgres");
            var context = SpringApplication.run(InvMgmtApplication.class, args);
            try {
                if (Arrays.asList(args).contains("--invmgmt.demo-data.enabled=true")) {
                    context.getBean(DemoDataLoader.class).load();
                }
                log.info("Local PostgreSQL: 127.0.0.1:15432/postgres, data directory: {}", root.resolve("postgres-data"));
            } catch (Exception exception) {
                context.close();
                throw exception;
            }
        } catch (Exception exception) {
            database.close();
            Runtime.getRuntime().removeShutdownHook(shutdown);
            throw exception;
        }
    }
}
