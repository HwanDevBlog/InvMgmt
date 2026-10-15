package com.hwandevblog.invmgmt;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 로컬 PostgreSQL의 데이터 디렉터리와 실행 잠금을 함께 관리한다.
 * 종료 시 서버와 잠금만 정리하고 기존 데이터는 다음 실행을 위해 보존한다.
 */
final class LocalPostgres implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;
    private EmbeddedPostgres postgres;
    private boolean closed;

    private LocalPostgres(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    static LocalPostgres start(Path root, int port) throws IOException {
        Files.createDirectories(root);
        var channel = FileChannel.open(root.resolve("database.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException exception) {
            channel.close();
            throw new IOException("Local database is already running: " + root, exception);
        } catch (IOException exception) {
            channel.close();
            throw exception;
        }
        if (lock == null) {
            channel.close();
            throw new IOException("Local database is already running: " + root);
        }
        var database = new LocalPostgres(channel, lock);
        try {
            // 데이터 보존과 디스크 동기화를 켜고 로컬 연결만 허용한다.
            database.postgres = EmbeddedPostgres.builder()
                    .setDataDirectory(root.resolve("postgres-data"))
                    .setCleanDataDirectory(false)
                    .setPort(port)
                    .setLocaleConfig("lc-messages", "C")
                    .setServerConfig("listen_addresses", "127.0.0.1")
                    .setServerConfig("fsync", "on")
                    .start();
            return database;
        } catch (IOException | RuntimeException exception) {
            try { database.close(); } catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            throw exception;
        }
    }

    String jdbcUrl() {
        return postgres.getJdbcUrl("postgres", "postgres");
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        // PostgreSQL 종료가 실패해도 실행 잠금과 파일 핸들은 반환한다.
        try {
            if (postgres != null) postgres.close();
        } finally {
            try { lock.release(); } finally { channel.close(); }
        }
    }
}
