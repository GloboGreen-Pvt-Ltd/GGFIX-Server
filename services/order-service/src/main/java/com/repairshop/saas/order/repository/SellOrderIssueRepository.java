package com.repairshop.saas.order.repository;

import com.repairshop.saas.order.entity.SellOrderIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface SellOrderIssueRepository extends JpaRepository<SellOrderIssue, UUID> {
    List<SellOrderIssue> findBySellOrderId(UUID sellOrderId);

    @Modifying
    @Transactional
    void deleteBySellOrderId(UUID sellOrderId);
}
