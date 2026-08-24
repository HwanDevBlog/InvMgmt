package com.hwandevblog.invmgmt;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;

public final class LocalApplication {

    private static EmbeddedPostgres postgres;

    private LocalApplication() {
    }

    public static void main(String[] args) throws IOException {
        postgres = EmbeddedPostgres.builder()
                .setLocaleConfig("lc-messages", "C")
                .start();
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "postgres");
        InvMgmtApplication.main(args);
    }
}
