package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.RepairNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RepairNoteRepository extends JpaRepository<RepairNote, UUID> {
    List<RepairNote> findByTicketIdOrderByCreatedAtDesc(UUID ticketId);
}
