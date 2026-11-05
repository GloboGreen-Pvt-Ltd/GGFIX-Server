package com.repairshop.saas.subscription.service;

import com.repairshop.saas.subscription.billing.SubscriptionTerms;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Human-facing numbers from Postgres sequences (migration 106), so they are
 * unique and gap-tolerant without a read-modify-write race.
 * Order no doubles as the Razorpay order {@code receipt} (≤ 40 chars).
 */
@Component
@RequiredArgsConstructor
public class NumberSequence {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    /** e.g. NGF-SUB-20261031-000042 */
    public String nextOrderNo() {
        Long n = jdbc.queryForObject("SELECT nextval('subscription_order_no_seq')", Long.class);
        return "NGF-SUB-" + LocalDate.now(clock.withZone(SubscriptionTerms.IST)).format(DAY)
                + "-" + String.format("%06d", n);
    }

    /** e.g. NGF-RCPT-2026-27-000042 (Indian financial year, April–March). */
    public String nextReceiptNo() {
        Long n = jdbc.queryForObject("SELECT nextval('subscription_receipt_no_seq')", Long.class);
        LocalDate today = LocalDate.now(clock.withZone(SubscriptionTerms.IST));
        int fyStart = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        return "NGF-RCPT-" + fyStart + "-" + String.format("%02d", (fyStart + 1) % 100)
                + "-" + String.format("%06d", n);
    }
}
