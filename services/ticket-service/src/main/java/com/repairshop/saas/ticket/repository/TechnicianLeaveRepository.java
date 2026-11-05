package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.TechnicianLeave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TechnicianLeaveRepository extends JpaRepository<TechnicianLeave, UUID> {

    List<TechnicianLeave> findByTechnicianIdOrderByRequestedAtDesc(UUID technicianId);

    List<TechnicianLeave> findByTechnicianIdAndStartDateBetweenOrderByRequestedAtDesc(
            UUID technicianId, LocalDate start, LocalDate end);

    java.util.Optional<TechnicianLeave> findByTechnicianIdAndId(UUID technicianId, UUID leaveId);

    List<TechnicianLeave> findByTechnicianIdInAndStatusOrderByRequestedAtDesc(Collection<UUID> technicianIds, String status);

    /**
     * Returns any PENDING/PROCESSING/APPROVED leave for this technician whose
     * [start,end] range overlaps the supplied window. Used to block duplicate
     * applications for the same period; CANCELLED and REJECTED rows are
     * intentionally ignored so a rejected employee can re-apply.
     */
    @Query("SELECT l FROM TechnicianLeave l " +
           "WHERE l.technicianId = :technicianId " +
           "  AND l.status IN ('PENDING','PROCESSING','APPROVED') " +
           "  AND l.startDate <= :end " +
           "  AND l.endDate >= :start")
    List<TechnicianLeave> findOverlapping(
            @Param("technicianId") UUID technicianId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
