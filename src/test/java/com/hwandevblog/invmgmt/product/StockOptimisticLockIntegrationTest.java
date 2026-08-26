package com.hwandevblog.invmgmt.product;

import com.hwandevblog.invmgmt.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StockOptimisticLockIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void detectsConcurrentUpdatesLoadedFromTheSameVersion() throws Exception {
        ProductResponse product = productService.create(
                new CreateProductRequest("ZZ-OPTIMISTIC-LOCK", "Optimistic Lock Product", 10));
        CountDownLatch stockLoaded = new CountDownLatch(2);
        CountDownLatch startUpdate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Attempt> reservation = () -> attemptReservation(
                product.id(), stockLoaded, startUpdate);
        Future<Attempt> first = executor.submit(reservation);
        Future<Attempt> second = executor.submit(reservation);

        try {
            assertThat(stockLoaded.await(10, TimeUnit.SECONDS)).isTrue();
            startUpdate.countDown();

            List<Attempt> attempts = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertThat(attempts).filteredOn(Attempt::succeeded).hasSize(1);
            assertThat(attempts).filteredOn(attempt -> !attempt.succeeded()).singleElement()
                    .satisfies(attempt -> assertThat(hasOptimisticLockCause(attempt.failure())).isTrue());

            Stock stock = stockRepository.findById(product.id()).orElseThrow();
            assertThat(stock.getQuantity()).isEqualTo(3);
            assertThat(stock.getVersion()).isEqualTo(1);
        } finally {
            startUpdate.countDown();
            executor.shutdownNow();
        }
    }

    private Attempt attemptReservation(long productId,
                                       CountDownLatch stockLoaded,
                                       CountDownLatch startUpdate) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                Stock stock = stockRepository.findById(productId).orElseThrow();
                stockLoaded.countDown();
                await(startUpdate);
                stock.reserve(7);
                stockRepository.flush();
            });
            return Attempt.success();
        } catch (RuntimeException exception) {
            return Attempt.failure(exception);
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating concurrent updates");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating concurrent updates", exception);
        }
    }

    private boolean hasOptimisticLockCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ObjectOptimisticLockingFailureException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record Attempt(boolean succeeded, Throwable failure) {

        static Attempt success() {
            return new Attempt(true, null);
        }

        static Attempt failure(Throwable failure) {
            return new Attempt(false, failure);
        }
    }
}
