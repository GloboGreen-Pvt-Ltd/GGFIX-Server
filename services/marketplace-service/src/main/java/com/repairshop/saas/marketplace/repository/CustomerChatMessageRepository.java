package com.repairshop.saas.marketplace.repository;

import com.repairshop.saas.marketplace.entity.CustomerChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerChatMessageRepository extends JpaRepository<CustomerChatMessage, UUID> {

    List<CustomerChatMessage> findByThreadIdOrderByCreatedAtAsc(UUID threadId);

    // Mark every still-unread message from the OTHER side as read. Caller passes
    // the sender they want to mark (e.g. SHOP marking CUSTOMER messages read).
    @Modifying
    @Query("UPDATE CustomerChatMessage m SET m.readAt = :now " +
           "WHERE m.threadId = :threadId AND m.sender = :sender AND m.readAt IS NULL")
    int markRead(@Param("threadId") UUID threadId,
                 @Param("sender") String sender,
                 @Param("now") Instant now);
}
