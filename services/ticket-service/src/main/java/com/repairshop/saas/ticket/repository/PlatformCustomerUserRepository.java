package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.PlatformCustomerUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformCustomerUserRepository extends JpaRepository<PlatformCustomerUser, UUID> {

    @Query("SELECT u FROM PlatformCustomerUser u " +
            "WHERE (u.isActive IS NULL OR u.isActive = true) " +
            "AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "     OR u.mobile LIKE CONCAT('%', :q, '%'))")
    List<PlatformCustomerUser> searchActive(@Param("q") String q, Pageable pageable);

    @Query("SELECT u FROM PlatformCustomerUser u WHERE u.mobile = :mobile")
    Optional<PlatformCustomerUser> findByMobile(@Param("mobile") String mobile);
}
