package com.repairshop.saas.subscription.dto.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.repairshop.saas.subscription.dto.v2.V2Views.IssuedBy;
import com.repairshop.saas.subscription.dto.v2.V2Views.ReceiptOwner;
import com.repairshop.saas.subscription.dto.v2.V2Views.ReceiptView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Field names are part of the cross-team contract. */
class ContractJsonTest {

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    @Test
    void receiptKeepsIsTaxInvoiceAndIsoDates() throws Exception {
        ReceiptView r = new ReceiptView("NGF-RCPT-2026-27-000001", null, "NGF-SUB-20261031-000001",
                new IssuedBy("Globogreen System & Technology Private Limited", null, null, null, null),
                new ReceiptOwner("Owner", "Shop", "9840000000", "o@example.com"), "Basic", "6 Months",
                Instant.parse("2026-10-31T06:30:00Z"), Instant.parse("2027-04-30T06:30:00Z"), List.of("Shop 1"),
                List.of(new V2Views.Line("Basic · 6 Months — first shop", 100)), 0, BigDecimal.ZERO, 0, 100, "INR",
                "pay_1", "upi", Instant.parse("2026-10-31T06:30:00Z"), "CAPTURED", "NearGFix Subscription Receipt",
                false, "LIVE");
        JsonNode json = mapper.readTree(mapper.writeValueAsString(r));

        assertTrue(json.has("isTaxInvoice"));
        assertFalse(json.has("taxInvoice"));
        assertEquals("2026-10-31T06:30:00Z", json.get("startsAt").asText());
        assertTrue(json.get("issuedBy").has("gstin"));
        assertTrue(json.get("issuedBy").get("gstin").isNull());
        assertEquals(100, json.get("totalPaise").asLong());
    }
}
