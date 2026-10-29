package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Staff app's "my jobs" query on an in-memory H2 database: parses at
 * startup, stays inside one technician + shop, and applies the status / q
 * filters (empty status list = any).
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.default_schema=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class TicketRepositoryAssignedSearchTest {

    @Autowired TicketRepository repo;

    private final UUID shopId = UUID.randomUUID();
    private final UUID me = UUID.randomUUID();

    private void ticket(UUID shop, UUID tech, String tracking, String status, String device) {
        repo.save(Ticket.builder().shopId(shop).customerId(UUID.randomUUID()).assignedTechnicianId(tech)
                .trackingId(tracking).bookingNumber(tracking).status(status).deviceDisplayName(device)
                .customerName("Nandha").customerPhone("8939615914").build());
    }

    @BeforeEach
    void seed() {
        ticket(shopId, me, "GTS-001", "IN_REPAIR", "Redmi Note 12");
        ticket(shopId, me, "GTS-002", "READY", "iPhone 13");
        ticket(shopId, me, "GTS-003", "DELIVERED", "Galaxy A52");
        ticket(shopId, UUID.randomUUID(), "GTS-004", "IN_REPAIR", "Redmi Note 12");
        ticket(UUID.randomUUID(), me, "GTS-005", "IN_REPAIR", "Redmi Note 12");
    }

    private List<String> search(boolean anyStatus, List<String> statuses, String q) {
        return repo.searchByAssignedTechnician(shopId, me, anyStatus, statuses, q, PageRequest.of(0, 20))
                .map(Ticket::getTrackingId).getContent().stream().sorted().toList();
    }

    @Test
    void noFiltersReturnsOnlyOwnJobsInOwnShop() {
        assertEquals(List.of("GTS-001", "GTS-002", "GTS-003"), search(true, List.of(), ""));
    }

    @Test
    void statusListAndQueryNarrowTheList() {
        assertEquals(List.of("GTS-001", "GTS-002"), search(false, List.of("IN_REPAIR", "READY"), ""));
        assertEquals(List.of("GTS-001"), search(true, List.of(), "redmi"));
        assertEquals(List.of("GTS-002"), search(false, List.of("READY"), "gts-00"));
        // The customer's phone is not searchable from the Staff app.
        assertEquals(List.of(), search(true, List.of(), "8939615914"));
    }

    @Test
    void countsGroupOwnJobsByStatus() {
        List<Object[]> rows = repo.countByAssignedTechnicianGroupByStatus(shopId, me);
        assertEquals(3, rows.size());
        assertEquals(3L, rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum());
    }
}
