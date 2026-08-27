package com.hwandevblog.invmgmt.product;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@Tag(name = "상품", description = "상품과 초기 재고를 관리합니다.")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "상품 등록")
    ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request);
    }

    @GetMapping
    @Operation(summary = "상품 목록 조회")
    List<ProductResponse> list() {
        return productService.list();
    }

    @GetMapping("/{productId}")
    @Operation(summary = "상품 단건 조회")
    ProductResponse get(@PathVariable long productId) {
        return productService.get(productId);
    }

    @PutMapping("/{productId}")
    @Operation(summary = "상품 수정")
    ProductResponse update(@PathVariable long productId,
                           @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(productId, request);
    }
}
