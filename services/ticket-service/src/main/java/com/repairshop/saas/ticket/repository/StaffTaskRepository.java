package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.StaffTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffTaskRepository extends JpaRepository<StaffTask, UUID> {

    Optional<StaffTask> findByShopIdAndId(UUID shopId, UUID id);

    Optional<StaffTask> findByShopIdAndClientRef(UUID shopId, String clientRef);

    List<StaffTask> findByShopIdAndTaskDateBetweenOrderByTaskDateAscStartTimeAsc(UUID shopId, LocalDate from, LocalDate to);

    /** An employee's own tasks (Employee App). */
    @Query("select distinct t from StaffTask t join t.assignees a "
         + "where t.shopId = :shopId and a.technicianId = :technicianId "
         + "and t.taskDate between :from and :to order by t.taskDate asc, t.startTime asc")
    List<StaffTask> findForTechnician(@Param("shopId") UUID shopId,
                                      @Param("technicianId") UUID technicianId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);
}
