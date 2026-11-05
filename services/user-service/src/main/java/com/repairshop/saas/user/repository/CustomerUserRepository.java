package com.repairshop.saas.user.repository;

import com.repairshop.saas.user.entity.CustomerUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerUserRepository extends JpaRepository<CustomerUser, UUID> {

    Optional<CustomerUser> findByEmail(String email);

    Optional<CustomerUser> findByMobile(String mobile);
}
