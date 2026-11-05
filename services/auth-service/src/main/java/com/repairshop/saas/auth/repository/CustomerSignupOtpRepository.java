package com.repairshop.saas.auth.repository;

import com.repairshop.saas.auth.entity.CustomerSignupOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerSignupOtpRepository extends JpaRepository<CustomerSignupOtp, String> {
}
