package com.repairshop.saas.ticket.config;

import com.repairshop.saas.ticket.entity.Technician;
import com.repairshop.saas.ticket.entity.Ticket;
import com.repairshop.saas.ticket.repository.TechnicianRepository;
import com.repairshop.saas.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Seeds sample tickets for E2E testing when the DB is empty.
 * Uses the same E2E_ADMIN_SHOP_ID as auth-service AdminSeeder. Assigns 2 tickets to E2E technician (technician / test).
 *
 * Disabled — the Bookings History screen must reflect only real database rows.
 * Re-enable by restoring the @Component annotation below.
 */
// @Component
@Profile("dev")
@Order(3)
@RequiredArgsConstructor
public class TicketSeeder implements CommandLineRunner {

    /** Must match AdminSeeder.E2E_ADMIN_SHOP_ID in auth-service. */
    private static final UUID E2E_ADMIN_SHOP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID E2E_CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    /** Auth E2E technician userId (login: technician). Resolved to technician.id in run(). */
    private static final UUID E2E_TECH_USER_ID = UUID.fromString("a992a80e-edf9-404e-8a4d-b8d344191cc5");
    /** Placeholder IDs when master-data is not available; replace with real IDs from GET /master/brands etc. for full E2E. */
    private static final UUID PLACEHOLDER_BRAND_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID PLACEHOLDER_MODEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    private final TicketRepository ticketRepository;
    private final TechnicianRepository technicianRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (ticketRepository.count() > 0)
            return;

        Optional<Technician> e2eTech = technicianRepository.findFirstByUserId(E2E_TECH_USER_ID);
        UUID assignedTechId = e2eTech.map(Technician::getId).orElse(null);

        ticketRepository.save(buildTicket("CSPEN1001", "Screen cracked", "CREATED", null,
                "Galaxy Z Fold7 (16GB 512GB)", "https://dummyassets.local/models/galaxy-z-fold-7.png"));
        ticketRepository.save(buildTicket("CSPEN1002", "Battery draining fast", "IN_DIAGNOSIS", assignedTechId,
                "Vivo Y200 5G (8GB 256GB)", "https://dummyassets.local/models/vivo-y200-5g.png"));
        ticketRepository.save(buildTicket("CSPEN1003", "Charging port not working", "IN_REPAIR", assignedTechId,
                "iPhone 15 Pro Max (256GB)", "https://dummyassets.local/models/iphone-15-pro-max.png"));
        ticketRepository.save(buildTicket("CSPEN1004", "Water damage", "CREATED", null,
                "Galaxy S24 Ultra (12GB 256GB)", "https://dummyassets.local/models/galaxy-s24-ultra.png"));
        ticketRepository.save(buildTicket("CSPEN1005", "Camera focus issue", "CREATED", null,
                "Pixel 9 Pro (12GB 256GB)", "https://dummyassets.local/models/pixel-9-pro.png"));
    }

    private Ticket buildTicket(String trackingId, String issue, String status, UUID assignedTechId,
                               String deviceDisplayName, String deviceImageUrl) {
        String priceItemsJson = """
                [
                  { "label": "Display Screen Combo", "amount": 10500 },
                  { "label": "Battery", "amount": 2500 }
                ]
                """;
        String missingPartsJson = """
                [
                  { "name": "Display", "missing": false, "damage": true, "details": "Cracked and flickering" },
                  { "name": "Back Door", "missing": false, "damage": true, "details": "Scratches on corners" }
                ]
                """;
        String devicePhotosJson = """
                {
                  "front": "https://dummyassets.local/device-photos/front.jpg",
                  "back": "https://dummyassets.local/device-photos/back.jpg",
                  "video": "https://dummyassets.local/device-photos/video.mp4"
                }
                """;

        return Ticket.builder()
                .shopId(E2E_ADMIN_SHOP_ID)
                .customerId(E2E_CUSTOMER_ID)
                .assignedTechnicianId(assignedTechId)
                .trackingId(trackingId)
                .brandId(PLACEHOLDER_BRAND_ID)
                .modelId(PLACEHOLDER_MODEL_ID)
                .issueDescription(issue)
                .estimatedPrice(new BigDecimal("12500.00"))
                .finalPrice(status.equals("COMPLETED") ? new BigDecimal("12000.00") : null)
                .status(status)
                .color("Black")
                .imei(null)
                .deviceDisplayName(deviceDisplayName)
                .deviceImageUrl(deviceImageUrl)
                .repairServicesSummary("Display Screen Combo, Battery")
                .priceItemsJson(priceItemsJson)
                .missingPartsJson(missingPartsJson)
                .devicePhotosJson(devicePhotosJson)
                .deviceSecurityType("PIN")
                .deviceSecurityValue("1234")
                .customerApproval(Boolean.TRUE)
                .build();
    }
}
