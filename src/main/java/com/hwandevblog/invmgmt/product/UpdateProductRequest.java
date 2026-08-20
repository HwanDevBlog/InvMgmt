package com.hwandevblog.invmgmt.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProductRequest(
        @NotBlank @Size(max = 150) String name,
        boolean active
) {
}
