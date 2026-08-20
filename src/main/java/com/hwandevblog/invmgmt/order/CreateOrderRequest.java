package com.hwandevblog.invmgmt.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank @Size(max = 50) String orderNumber,
        @NotEmpty List<@Valid Line> lines
) {
    public record Line(
            @NotNull @Positive Long productId,
            @Positive long quantity
    ) {
    }
}
