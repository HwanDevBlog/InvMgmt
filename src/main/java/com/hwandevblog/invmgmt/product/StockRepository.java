package com.hwandevblog.invmgmt.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    @Override
    @EntityGraph(attributePaths = "product")
    List<Stock> findAll();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select stock from Stock stock where stock.productId = :productId")
    Optional<Stock> findByIdForUpdate(@Param("productId") Long productId);
}
