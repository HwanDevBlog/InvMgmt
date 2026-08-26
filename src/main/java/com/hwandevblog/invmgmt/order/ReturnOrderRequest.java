package com.hwandevblog.invmgmt.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public record ReturnOrderRequest(
        @NotEmpty @Size(max = 100) List<@Valid Line> lines
) {
    String canonicalIdentity() {
        // 항목 순서가 달라도 같은 반품 요청이면 동일한 멱등 요청으로 판단한다.
        return lines.stream()
                .sorted(Comparator.comparing(Line::orderLineId))
                .map(line -> line.orderLineId() + ":" + line.quantity())
                .collect(Collectors.joining(","));
    }

    public record Line(
            @NotNull @Positive Long orderLineId,
            @Positive long quantity
    ) {
    }
}
