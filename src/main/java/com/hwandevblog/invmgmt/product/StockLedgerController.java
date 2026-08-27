package com.hwandevblog.invmgmt.product;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stock-ledgers")
@Tag(name = "재고 거래 이력", description = "재고 수량이 변경된 원장을 조회합니다.")
public class StockLedgerController {

    private final StockLedgerQueryService stockLedgerQueryService;

    public StockLedgerController(StockLedgerQueryService stockLedgerQueryService) {
        this.stockLedgerQueryService = stockLedgerQueryService;
    }

    @GetMapping
    @Operation(summary = "재고 거래 이력 조회")
    List<StockLedgerResponse> list() {
        return stockLedgerQueryService.list();
    }
}
