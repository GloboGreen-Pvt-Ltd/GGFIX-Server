package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.RepairNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepairNoteRepository extends JpaRepository<RepairNote, UUID> {
    List<RepairNote> findByTicketIdOrderByCreatedAtDesc(UUID ticketId);

    // Scoped lookup for the edit endpoint. The ticket is already checked against
    // the caller's shop, so matching on ticketId as well stops a note id from
    // one ticket being edited through another ticket's URL.
    Optional<RepairNote> findByIdAndTicketId(UUID id, UUID ticketId);
}
