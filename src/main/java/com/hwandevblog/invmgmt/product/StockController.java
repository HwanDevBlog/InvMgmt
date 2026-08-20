package com.hwandevblog.invmgmt.product;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final ProductService productService;

    public StockController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    List<StockResponse> list() {
        return productService.listStocks();
    }
}
