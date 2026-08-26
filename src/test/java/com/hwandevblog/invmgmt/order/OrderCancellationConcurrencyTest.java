package com.hwandevblog.invmgmt.order;

import com.hwandevblog.invmgmt.PostgresIntegrationTest;
import com.hwandevblog.invmgmt.common.BusinessConflictException;
import com.hwandevblog.invmgmt.product.CreateProductRequest;
import com.hwandevblog.invmgmt.product.ProductResponse;
import com.hwandevblog.invmgmt.product.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderCancellationConcurrencyTest extends PostgresIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private PurchaseOrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void restoresInventoryOnlyOnceWhenCancellationRequestsRace() throws Exception {
        ProductResponse product = productService.create(new CreateProductRequest(
                "ZZ-CANCEL-CONCURRENT", "Concurrent Cancel Product", 10));
        OrderResponse order = orderService.create(new CreateOrderRequest(
                "ZZ-CANCEL-CONCURRENT-ORDER",
                List.of(new CreateOrderRequest.Line(product.id(), 3))));
        orderService.reserve(order.id());
        orderService.confirm(order.id());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Attempt> cancellation = () -> {
            ready.countDown();
            await(start);
            try {
                return Attempt.success(orderService.cancel(order.id()));
            } catch (RuntimeException exception) {
                return Attempt.failure(exception);
            }
        };
        Future<Attempt> first = executor.submit(cancellation);
        Future<Attempt> second = executor.submit(cancellation);

        try {
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Attempt> attempts = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertThat(attempts).filteredOn(Attempt::succeeded).hasSize(1);
            assertThat(attempts).filteredOn(attempt -> !attempt.succeeded()).singleElement()
                    .satisfies(attempt -> assertThat(attempt.failure())
                            .isInstanceOf(BusinessConflictException.class)
                            .hasMessage("Only confirmed orders can be canceled"));

            Integer cancellationLedgerCount = jdbcTemplate.queryForObject(
                    "select count(*) from stock_ledger "
                            + "where product_id = ? and movement_type = 'CANCEL'",
                    Integer.class,
                    product.id());

            assertThat(orderService.get(order.id()).status()).isEqualTo(OrderStatus.CANCELED);
            assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(10);
            assertThat(cancellationLedgerCount).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating cancellation requests");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating cancellation requests", exception);
        }
    }

    private record Attempt(boolean succeeded, OrderResponse response, RuntimeException failure) {

        static Attempt success(OrderResponse response) {
            return new Attempt(true, response, null);
        }

        static Attempt failure(RuntimeException failure) {
            return new Attempt(false, null, failure);
        }
    }
}
