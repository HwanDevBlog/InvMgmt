package com.hwandevblog.invmgmt.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "must contain only letters, numbers, underscore, or hyphen")
        String sku,

        @NotBlank
        @Size(max = 150)
        String name,

        @PositiveOrZero
        long initialQuantity
) {
}
