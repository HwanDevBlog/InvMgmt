package com.hwandevblog.invmgmt.product;

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
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request);
    }

    @GetMapping
    List<ProductResponse> list() {
        return productService.list();
    }

    @GetMapping("/{productId}")
    ProductResponse get(@PathVariable long productId) {
        return productService.get(productId);
    }

    @PutMapping("/{productId}")
    ProductResponse update(@PathVariable long productId,
                           @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(productId, request);
    }
}
