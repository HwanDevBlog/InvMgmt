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
class OrderReturnConcurrencyTest extends PostgresIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private PurchaseOrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void preventsConcurrentReturnsFromExceedingOrderedQuantity() throws Exception {
        ProductResponse product = productService.create(new CreateProductRequest(
                "ZZ-RETURN-CONCURRENT", "Concurrent Return Product", 10));
        OrderResponse order = orderService.create(new CreateOrderRequest(
                "ZZ-RETURN-CONCURRENT-ORDER",
                List.of(new CreateOrderRequest.Line(product.id(), 5))));
        orderService.reserve(order.id());
        OrderResponse confirmedOrder = orderService.confirm(order.id());
        long orderId = confirmedOrder.id();
        long orderLineId = confirmedOrder.lines().getFirst().id();
        ReturnOrderRequest request = new ReturnOrderRequest(
                List.of(new ReturnOrderRequest.Line(orderLineId, 4)));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Attempt> returnAttempt = () -> {
            ready.countDown();
            await(start);
            try {
                return Attempt.success(orderService.returnItems(orderId, request));
            } catch (RuntimeException exception) {
                return Attempt.failure(exception);
            }
        };
        Future<Attempt> first = executor.submit(returnAttempt);
        Future<Attempt> second = executor.submit(returnAttempt);

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
                            .hasMessage("Return quantity exceeds remaining quantity"));

            OrderResponse currentOrder = orderService.get(orderId);
            Integer returnLedgerCount = jdbcTemplate.queryForObject(
                    "select count(*) from stock_ledger "
                            + "where product_id = ? and movement_type = 'RETURN'",
                    Integer.class,
                    product.id());

            assertThat(currentOrder.status()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(currentOrder.lines().getFirst().returnedQuantity()).isEqualTo(4);
            assertThat(productService.get(product.id()).stockQuantity()).isEqualTo(9);
            assertThat(returnLedgerCount).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating return requests");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating return requests", exception);
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
