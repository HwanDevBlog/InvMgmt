package com.hwandevblog.invmgmt.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwandevblog.invmgmt.common.BusinessConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository recordRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRecordRepository recordRepository,
                              ObjectMapper objectMapper) {
        this.recordRepository = recordRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public <T> T execute(String key,
                         String requestIdentity,
                         Class<T> responseType,
                         Supplier<T> action) {
        validateKey(key);
        String requestHash = sha256(requestIdentity);
        int claimed = recordRepository.claim(key, requestHash);
        IdempotencyRecord record = recordRepository.findByKey(key)
                .orElseThrow(() -> new IllegalStateException("Claimed idempotency key was not found"));

        record.verifyRequestHash(requestHash);
        if (claimed == 0) {
            return replay(record, responseType);
        }

        T response = action.get();
        record.complete(200, writeResponse(response));
        return response;
    }

    private <T> T replay(IdempotencyRecord record, Class<T> responseType) {
        if (record.getStatus() == IdempotencyStatus.PROCESSING) {
            throw new BusinessConflictException(
                    "Request with this idempotency key is still processing");
        }
        if (record.getStatus() != IdempotencyStatus.COMPLETED
                || record.getResponseBody() == null) {
            throw new BusinessConflictException(
                    "Previous request with this idempotency key did not complete");
        }
        try {
            return objectMapper.readValue(record.getResponseBody(), responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read idempotent response", exception);
        }
    }

    private String writeResponse(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not store idempotent response", exception);
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key must not be blank");
        }
        if (key.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key must not exceed 100 characters");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
