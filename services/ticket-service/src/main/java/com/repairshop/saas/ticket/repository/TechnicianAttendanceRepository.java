package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.TechnicianAttendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TechnicianAttendanceRepository extends JpaRepository<TechnicianAttendance, UUID> {

    List<TechnicianAttendance> findByTechnicianIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
            UUID technicianId, LocalDate start, LocalDate end);

    Optional<TechnicianAttendance> findByTechnicianIdAndAttendanceDate(UUID technicianId, LocalDate date);
}
