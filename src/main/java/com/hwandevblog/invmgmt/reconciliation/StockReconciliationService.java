package com.hwandevblog.invmgmt.reconciliation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class StockReconciliationService {

    private final StockReconciliationMapper reconciliationMapper;

    public StockReconciliationService(StockReconciliationMapper reconciliationMapper) {
        this.reconciliationMapper = reconciliationMapper;
    }

    public List<StockReconciliationResponse> findAll() {
        return reconciliationMapper.findAll();
    }
}
