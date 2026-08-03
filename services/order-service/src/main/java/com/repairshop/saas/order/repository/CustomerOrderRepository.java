package com.repairshop.saas.order.repository;

import com.repairshop.saas.order.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {
    List<CustomerOrder> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId);
    List<CustomerOrder> findByCustomerUserIdAndOrderTypeOrderByCreatedAtDesc(UUID customerUserId, String orderType);
    Optional<CustomerOrder> findByOrderNumber(String orderNumber);
    // Used by per-booking GET to widen the ownership check: if the caller owns a
    // customer_orders row pointing at this booking, treat them as the booking's
    // customer even when repair_bookings.customer_user_id has drifted (e.g. the
    // ticket mirror first wrote the booking before the customer linked their
    // platform account, or the customer re-linked under a new customer_users id).
    boolean existsByReferenceIdAndCustomerUserId(UUID referenceId, UUID customerUserId);
}
