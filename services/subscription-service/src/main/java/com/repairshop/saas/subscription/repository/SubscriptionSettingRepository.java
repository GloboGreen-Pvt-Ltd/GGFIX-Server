package com.repairshop.saas.subscription.repository;

import com.repairshop.saas.subscription.entity.SubscriptionSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionSettingRepository extends JpaRepository<SubscriptionSetting, String> {
}
