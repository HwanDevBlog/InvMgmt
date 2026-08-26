package com.hwandevblog.invmgmt.product;

import com.hwandevblog.invmgmt.PostgresIntegrationTest;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StockPessimisticLockIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void blocksAnotherTransactionWhileWriteLockIsHeld() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("ZZ-PESSIMISTIC-LOCK", "Pessimistic Lock Product", 10));
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> lockHolder = executor.submit(() -> holdWriteLock(
                product.id(), lockAcquired, releaseLock));

        try {
            assertThat(lockAcquired.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Throwable> competingReader = executor.submit(
                    () -> attemptCompetingWriteLock(product.id()));

            Throwable failure = competingReader.get(10, TimeUnit.SECONDS);

            assertThat(failure).isNotNull();
            assertThat(hasPessimisticLockCause(failure)).isTrue();
        } finally {
            releaseLock.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    private void holdWriteLock(long productId,
                               CountDownLatch lockAcquired,
                               CountDownLatch releaseLock) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            stockRepository.findByIdForUpdate(productId).orElseThrow();
            lockAcquired.countDown();
            await(releaseLock);
        });
    }

    private Throwable attemptCompetingWriteLock(long productId) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbcTemplate.execute("set local lock_timeout = '500ms'");
                stockRepository.findByIdForUpdate(productId).orElseThrow();
            });
            return null;
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while holding pessimistic lock");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding pessimistic lock", exception);
        }
    }

    private boolean hasPessimisticLockCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof PessimisticLockingFailureException
                    || current instanceof PessimisticLockException
                    || current instanceof LockTimeoutException) {
                return true;
            }
            if (current instanceof SQLException sqlException
                    && "55P03".equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
