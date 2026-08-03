package com.repairshop.saas.user.repository;

import com.repairshop.saas.user.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

    List<CustomerAddress> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId);
}
