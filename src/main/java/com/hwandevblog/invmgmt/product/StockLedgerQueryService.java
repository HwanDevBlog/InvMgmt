package com.hwandevblog.invmgmt.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class StockLedgerQueryService {

    private final StockLedgerRepository stockLedgerRepository;

    public StockLedgerQueryService(StockLedgerRepository stockLedgerRepository) {
        this.stockLedgerRepository = stockLedgerRepository;
    }

    public List<StockLedgerResponse> list() {
        return stockLedgerRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(StockLedgerResponse::from)
                .toList();
    }
}
