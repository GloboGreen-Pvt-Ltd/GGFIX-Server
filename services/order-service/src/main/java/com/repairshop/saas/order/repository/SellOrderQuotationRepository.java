package com.repairshop.saas.order.repository;

import com.repairshop.saas.order.entity.SellOrderQuotation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SellOrderQuotationRepository extends JpaRepository<SellOrderQuotation, UUID> {
    List<SellOrderQuotation> findBySellOrderId(UUID sellOrderId);
}
