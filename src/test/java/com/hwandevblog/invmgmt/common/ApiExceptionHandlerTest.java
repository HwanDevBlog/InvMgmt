package com.hwandevblog.invmgmt.common;

import com.hwandevblog.invmgmt.product.Stock;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void returnsConflictWhenOptimisticLockDetectsConcurrentUpdate() {
        ObjectOptimisticLockingFailureException exception =
                new ObjectOptimisticLockingFailureException(Stock.class, 1L);

        ProblemDetail detail = handler.handleOptimisticLock(exception);

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(detail.getDetail()).isEqualTo("Stock was changed by another request");
    }
}
