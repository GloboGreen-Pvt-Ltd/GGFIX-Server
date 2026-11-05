package com.repairshop.saas.user.repository;

import com.repairshop.saas.user.entity.CustomerSavedDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerSavedDeviceRepository extends JpaRepository<CustomerSavedDevice, UUID> {

    List<CustomerSavedDevice> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId);
}
