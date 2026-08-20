package com.hwandevblog.invmgmt.product;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockRepository extends JpaRepository<Stock, Long> {

    @Override
    @EntityGraph(attributePaths = "product")
    List<Stock> findAll();
}
