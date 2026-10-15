package com.hwandevblog.invmgmt;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 PostgreSQL을 종료하고 다시 실행해 로컬 데이터 보존과 중복 실행 차단을 검증한다.
 * 시연 데이터 폴더 대신 테스트 전용 임시 디렉터리를 사용한다.
 */
class LocalPostgresTest {

    @TempDir
    Path root;

    @Test
    void preservesDataAndMigrationHistoryAfterRestart() throws Exception {
        int port;
        try (var database = LocalPostgres.start(root, 0)) {
            migrate(database);
            try (var connection = DriverManager.getConnection(database.jdbcUrl(), "postgres", "postgres");
                 var sql = connection.createStatement()) {
                try (var result = sql.executeQuery("select current_setting('port')::integer")) {
                    result.next();
                    port = result.getInt(1);
                }
                sql.execute("insert into products(sku,name,active,created_at,updated_at) values ('PERSIST-001','재시작 보존',true,now(),now())");
                sql.execute("insert into stocks(product_id,quantity,version) select id,17,2 from products where sku='PERSIST-001'");
            }
        }
        try (var database = LocalPostgres.start(root, port)) {
            migrate(database);
            try (var connection = DriverManager.getConnection(database.jdbcUrl(), "postgres", "postgres");
                 var sql = connection.createStatement();
                 var result = sql.executeQuery("select p.name,s.quantity,s.version,current_setting('fsync'),"
                         + "(select count(*) from flyway_schema_history where success),current_setting('port')::integer "
                         + "from products p join stocks s on s.product_id=p.id where p.sku='PERSIST-001'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("재시작 보존");
                assertThat(result.getLong(2)).isEqualTo(17);
                assertThat(result.getLong(3)).isEqualTo(2);
                assertThat(result.getString(4)).isEqualTo("on");
                assertThat(result.getInt(5)).isEqualTo(2);
                assertThat(result.getInt(6)).isEqualTo(port);
                assertThat(result.next()).isFalse();
            }
        }
    }

    @Test
    void rejectsAnotherInstanceUsingTheSameDataDirectory() throws Exception {
        try (var database = LocalPostgres.start(root, 0)) {
            assertThatThrownBy(() -> LocalPostgres.start(root, 0))
                    .isInstanceOf(IOException.class).hasMessageContaining("already running");
        }
        // 중복 시도가 기존 잠금을 해제하지 않고 정상 종료 후에는 재사용할 수 있어야 한다.
        try (var database = LocalPostgres.start(root, 0)) {
            assertThat(database.jdbcUrl()).contains("postgres");
        }
    }

    private void migrate(LocalPostgres database) {
        Flyway.configure().dataSource(database.jdbcUrl(), "postgres", "postgres")
                .locations("classpath:db/migration").load().migrate();
    }
}
