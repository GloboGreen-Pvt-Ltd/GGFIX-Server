package com.repairshop.saas.subscription.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repairshop.saas.subscription.dto.v2.V2Views.AuditLogView;
import com.repairshop.saas.subscription.entity.SubscriptionAuditLog;
import com.repairshop.saas.subscription.repository.SubscriptionAuditLogRepository;
import com.repairshop.saas.subscription.security.CurrentUser.Actor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** subscription_audit_logs writer/reader. Writes join the caller's transaction. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String SYSTEM_ROLE = "SYSTEM";

    private final SubscriptionAuditLogRepository repository;
    private final ObjectMapper mapper;

    public void log(Actor actor, String action, String entityType, String entityId, Object details) {
        log(actor != null ? actor.userId() : null, actor != null ? actor.primaryRole() : SYSTEM_ROLE,
                action, entityType, entityId, details);
    }

    public void log(UUID actorUserId, String actorRole, String action, String entityType, String entityId,
                    Object details) {
        String json = null;
        try {
            json = details == null ? null : mapper.writeValueAsString(details);
        } catch (Exception e) {
            log.warn("Audit details not serialisable for {}: {}", action, e.toString());
        }
        repository.save(SubscriptionAuditLog.builder()
                .actorUserId(actorUserId)
                .actorRole(actorRole)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(json)
                .build());
    }

    @Transactional(readOnly = true)
    public List<AuditLogView> recent(Integer limit) {
        int n = limit == null ? 100 : Math.max(1, Math.min(limit, 500));
        return repository.findAllByOrderByAtDesc(PageRequest.of(0, n)).stream()
                .map(a -> new AuditLogView(a.getId(), a.getAt(), a.getActorUserId(), a.getActorRole(),
                        a.getAction(), a.getEntityType(), a.getEntityId(), parse(a.getDetails())))
                .toList();
    }

    private JsonNode parse(String json) {
        if (json == null) return null;
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return mapper.getNodeFactory().textNode(json);
        }
    }
}
