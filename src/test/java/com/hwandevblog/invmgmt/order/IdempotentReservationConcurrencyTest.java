package com.hwandevblog.invmgmt.order;

import com.hwandevblog.invmgmt.PostgresIntegrationTest;
import com.hwandevblog.invmgmt.common.BusinessConflictException;
import com.hwandevblog.invmgmt.idempotency.IdempotencyService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IdempotentReservationConcurrencyTest extends PostgresIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private PurchaseOrderService orderService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void processesConcurrentRequestsWithSameKeyOnlyOnce() throws Exception {
        ProductResponse product = productService.create(new CreateProductRequest(
                "ZZ-IDEMPOTENCY-CONCURRENT", "Concurrent Idempotency Product", 10));
        OrderResponse order = orderService.create(new CreateOrderRequest(
                "ZZ-IDEMPOTENCY-CONCURRENT-ORDER",
                List.of(new CreateOrderRequest.Line(product.id(), 3))));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<OrderResponse> reservation = () -> {
            ready.countDown();
            await(start);
            return idempotencyService.execute(
                    "concurrent-reservation-key",
                    "RESERVE_ORDER:" + order.id(),
                    OrderResponse.class,
                    () -> orderService.reserve(order.id()));
        };
        Future<OrderResponse> first = executor.submit(reservation);
        Future<OrderResponse> second = executor.submit(reservation);

        try {
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<OrderResponse> responses = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertThat(responses)
                    .extracting(OrderResponse::status)
                    .containsOnly(OrderStatus.RESERVED);
            assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(7);

            Integer reservationLedgerCount = jdbcTemplate.queryForObject(
                    "select count(*) from stock_ledger "
                            + "where product_id = ? and movement_type = 'RESERVE'",
                    Integer.class,
                    product.id());
            Integer idempotencyRecordCount = jdbcTemplate.queryForObject(
                    "select count(*) from idempotency_keys "
                            + "where idempotency_key = 'concurrent-reservation-key' "
                            + "and status = 'COMPLETED'",
                    Integer.class);

            assertThat(reservationLedgerCount).isEqualTo(1);
            assertThat(idempotencyRecordCount).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void rollsBackIdempotencyClaimWhenReservationFails() {
        ProductResponse product = productService.create(new CreateProductRequest(
                "ZZ-IDEMPOTENCY-ROLLBACK", "Idempotency Rollback Product", 2));
        OrderResponse order = orderService.create(new CreateOrderRequest(
                "ZZ-IDEMPOTENCY-ROLLBACK-ORDER",
                List.of(new CreateOrderRequest.Line(product.id(), 3))));

        assertThatThrownBy(() -> idempotencyService.execute(
                "failed-reservation-key",
                "RESERVE_ORDER:" + order.id(),
                OrderResponse.class,
                () -> orderService.reserve(order.id())))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Insufficient stock");

        Integer idempotencyRecordCount = jdbcTemplate.queryForObject(
                "select count(*) from idempotency_keys "
                        + "where idempotency_key = 'failed-reservation-key'",
                Integer.class);
        Integer reservationLedgerCount = jdbcTemplate.queryForObject(
                "select count(*) from stock_ledger "
                        + "where product_id = ? and movement_type = 'RESERVE'",
                Integer.class,
                product.id());

        assertThat(idempotencyRecordCount).isZero();
        assertThat(reservationLedgerCount).isZero();
        assertThat(orderService.get(order.id()).status()).isEqualTo(OrderStatus.CREATED);
        assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(2);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating idempotent requests");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while coordinating idempotent requests", exception);
        }
    }
}
