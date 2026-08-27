package com.hwandevblog.invmgmt.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface StockLedgerRepository extends JpaRepository<StockLedger, Long> {

    @EntityGraph(attributePaths = "product")
    List<StockLedger> findAllByOrderByCreatedAtDesc();
}
