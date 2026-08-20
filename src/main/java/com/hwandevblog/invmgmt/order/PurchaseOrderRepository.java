package com.hwandevblog.invmgmt.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    boolean existsByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = {"lines", "lines.product"})
    Optional<PurchaseOrder> findWithLinesById(Long id);
}
