package com.repairshop.saas.common.subscription;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * v2 plan codes are enforced from the limit columns on the subscriptions row;
 * legacy FREE_TRIAL / BASIC rows keep the enum behaviour; unknown codes still
 * fail open.
 */
class SubscriptionLimitServiceStoredPlanTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SubscriptionLimitService service = new SubscriptionLimitService(jdbc, true);
    private final UUID owner = UUID.randomUUID();
    private final Instant future = Instant.now().plus(90, ChronoUnit.DAYS);

    @Test
    void legacyBasicRowIgnoresColumnsAndUsesEnum() throws SQLException {
        // Columns deliberately disagree with the enum: enforcement must not read them for BASIC.
        OwnerSubscription sub = service.fromRow(row("BASIC", "BASIC", "ACTIVE", 3, 1, 1, 1, true, future), owner);

        assertEquals(SubscriptionPlan.BASIC, sub.plan());
        assertNull(sub.storedPlan());
        LimitCheck employees = service.evaluate(sub, SubscriptionFeature.EMPLOYEES, 50);
        assertTrue(employees.allowed());
        assertTrue(employees.unlimited());
        LimitCheck shops = service.evaluate(sub, SubscriptionFeature.SHOPS, 3);
        assertFalse(shops.allowed());
        assertEquals(3, shops.limit());
        assertEquals("BASIC", shops.plan());
        assertEquals("Basic", shops.planName());
    }

    @Test
    void legacyTrialRowKeepsEnumLimits() throws SQLException {
        OwnerSubscription sub = service.fromRow(row("FREE_TRIAL", "FREE_TRIAL", "FREE_TRIAL", 1, 9, 9, 9, true, future), owner);

        assertEquals(SubscriptionPlan.FREE_TRIAL, sub.plan());
        assertEquals(3, service.evaluate(sub, SubscriptionFeature.EMPLOYEES, 0).limit());
        assertEquals(2, service.evaluate(sub, SubscriptionFeature.SHOPS, 0).limit());
        assertEquals(5, service.evaluate(sub, SubscriptionFeature.SELL_ORDERS, 0).limit());
    }

    @Test
    void v2PlanIsEnforcedFromRowColumns() throws SQLException {
        stubMaster("BASIC_6M", "Basic");
        OwnerSubscription sub = service.resolveStoredPlan(
                service.fromRow(row("BASIC_6M", "BASIC_6M", "ACTIVE", 2, 2, 4, null, true, future), owner));

        assertNull(sub.plan());
        assertNotNull(sub.storedPlan());
        assertEquals("BASIC_6M", sub.planCode());
        assertEquals("Basic", sub.planName());

        assertTrue(service.evaluate(sub, SubscriptionFeature.EMPLOYEES, 3).allowed());
        LimitCheck full = service.evaluate(sub, SubscriptionFeature.EMPLOYEES, 4);
        assertFalse(full.allowed());
        assertEquals(LimitCheck.REASON_LIMIT_REACHED, full.reason());
        assertEquals(4, full.limit());
        assertTrue(full.message().contains("4 employees") && full.message().contains("Basic plan"));

        assertFalse(service.evaluate(sub, SubscriptionFeature.SHOPS, 2).allowed());
        assertTrue(service.evaluate(sub, SubscriptionFeature.SELL_ORDERS, 10_000).unlimited());
        assertTrue(service.hasCapability(sub, SubscriptionCapability.PICKUP_SERVICE));
        assertTrue(service.hasCapability(sub, SubscriptionCapability.NEW_SERVICE_BOOKING));
        assertTrue(service.hasCapability(sub, SubscriptionCapability.MULTIPLE_SHOPS));
    }

    @Test
    void expiredV2PlanBlocksAdditions() throws SQLException {
        stubMaster("PREMIUM_6M", "Premium");
        OwnerSubscription sub = service.resolveStoredPlan(service.fromRow(
                row("PREMIUM_6M", "PREMIUM_6M", "ACTIVE", 1, 1, 10, null, true, Instant.now().minusSeconds(60)), owner));

        LimitCheck check = service.evaluate(sub, SubscriptionFeature.EMPLOYEES, 0);
        assertFalse(check.allowed());
        assertEquals(LimitCheck.REASON_SUBSCRIPTION_EXPIRED, check.reason());
        assertFalse(service.hasCapability(sub, SubscriptionCapability.PICKUP_SERVICE));
    }

    @Test
    void unknownPlanCodeStillFailsOpen() throws SQLException {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("TYPO_PLAN"))).thenReturn(List.of());
        OwnerSubscription sub = service.resolveStoredPlan(
                service.fromRow(row("TYPO_PLAN", "TYPO_PLAN", "ACTIVE", 1, 1, 1, 1, false, future), owner));

        assertFalse(sub.resolved());
        LimitCheck check = service.evaluate(sub, SubscriptionFeature.EMPLOYEES, 500);
        assertTrue(check.allowed());
        assertEquals(LimitCheck.REASON_NO_SUBSCRIPTION, check.reason());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void stubMaster(String code, String name) {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(code))).thenAnswer(inv -> {
            ResultSet master = mock(ResultSet.class);
            when(master.getString("name")).thenReturn(name);
            when(master.getObject("service_booking_enabled")).thenReturn(Boolean.TRUE);
            RowMapper<Object> mapper = inv.getArgument(1);
            return List.of(mapper.mapRow(master, 0));
        });
    }

    private static ResultSet row(String type, String code, String status, Integer shopCount, Integer shopLimit,
                                 Integer employeeLimit, Integer sellLimit, Boolean pickup, Instant end)
            throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("subscription_type")).thenReturn(type);
        when(rs.getString("plan_code")).thenReturn(code);
        when(rs.getString("status")).thenReturn(status);
        when(rs.getObject("shop_count")).thenReturn(shopCount);
        when(rs.getObject("shop_limit")).thenReturn(shopLimit);
        when(rs.getObject("employee_limit")).thenReturn(employeeLimit);
        when(rs.getObject("sell_limit")).thenReturn(sellLimit);
        when(rs.getObject("pickup_service_enabled")).thenReturn(pickup);
        when(rs.getTimestamp("inactive_date")).thenReturn(Timestamp.from(end));
        return rs;
    }
}
