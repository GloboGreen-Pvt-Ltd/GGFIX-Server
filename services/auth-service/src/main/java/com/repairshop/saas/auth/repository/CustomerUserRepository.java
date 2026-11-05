package com.repairshop.saas.auth.repository;

import com.repairshop.saas.auth.entity.CustomerUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerUserRepository extends JpaRepository<CustomerUser, UUID> {

    Optional<CustomerUser> findByMobile(String mobile);

    Optional<CustomerUser> findByEmail(String email);

    boolean existsByMobile(String mobile);

    boolean existsByEmail(String email);
}
