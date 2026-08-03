package com.repairshop.saas.order.repository;

import com.repairshop.saas.order.entity.PlatformTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlatformTicketRepository extends JpaRepository<PlatformTicket, UUID> {
}
