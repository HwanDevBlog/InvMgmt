package com.hwandevblog.invmgmt.reconciliation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reconciliations/stocks")
public class StockReconciliationController {

    private final StockReconciliationService reconciliationService;

    public StockReconciliationController(StockReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping
    List<StockReconciliationResponse> findAll() {
        return reconciliationService.findAll();
    }
}
