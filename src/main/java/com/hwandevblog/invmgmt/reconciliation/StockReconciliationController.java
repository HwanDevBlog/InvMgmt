package com.hwandevblog.invmgmt.reconciliation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reconciliations/stocks")
@Tag(name = "재고 정합성", description = "현재 재고와 재고 원장의 합계를 대사합니다.")
public class StockReconciliationController {

    private final StockReconciliationService reconciliationService;

    public StockReconciliationController(StockReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping
    @Operation(summary = "재고 정합성 대사")
    List<StockReconciliationResponse> findAll() {
        return reconciliationService.findAll();
    }
}
