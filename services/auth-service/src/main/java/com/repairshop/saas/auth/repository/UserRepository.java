package com.repairshop.saas.auth.repository;

import com.repairshop.saas.auth.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByShop_IdAndEmail(UUID shopId, String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByShop_IdAndEmail(UUID shopId, String email);

    List<User> findByShop_IdAndRole(UUID shopId, String role);

    List<User> findByShop_IdOrderByEmailAsc(UUID shopId);

    List<User> findByRoleOrderByCreatedAtDesc(String role);
}
