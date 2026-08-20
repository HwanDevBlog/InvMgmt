package com.hwandevblog.invmgmt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DatabaseIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void usesPostgreSql16AndAppliesFlywayMigration() {
        Integer serverVersion = jdbcTemplate.queryForObject(
                "select current_setting('server_version_num')::integer", Integer.class);
        Integer migrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);

        assertThat(serverVersion).isBetween(160000, 169999);
        assertThat(migrationCount).isEqualTo(1);
    }
}
