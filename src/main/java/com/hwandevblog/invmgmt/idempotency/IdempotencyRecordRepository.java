package com.hwandevblog.invmgmt.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    @Modifying
    @Query(value = """
            insert into idempotency_keys (
                idempotency_key, request_hash, status, created_at, updated_at
            ) values (
                :key, :requestHash, 'PROCESSING', current_timestamp, current_timestamp
            )
            on conflict (idempotency_key) do nothing
            """, nativeQuery = true)
    int claim(@Param("key") String key, @Param("requestHash") String requestHash);

    Optional<IdempotencyRecord> findByKey(String key);
}
