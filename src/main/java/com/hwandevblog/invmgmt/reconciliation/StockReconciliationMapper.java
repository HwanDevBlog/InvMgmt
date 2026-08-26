package com.hwandevblog.invmgmt.reconciliation;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface StockReconciliationMapper {

    List<StockReconciliationResponse> findAll();
}
