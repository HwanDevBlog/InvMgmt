package com.hwandevblog.invmgmt.product;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@Tag(name = "재고", description = "상품별 현재 재고를 조회합니다.")
public class StockController {

    private final ProductService productService;

    public StockController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "재고 목록 조회")
    List<StockResponse> list() {
        return productService.listStocks();
    }
}
