package com.repairshop.saas.ticket.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.repairshop.saas.ticket.config.DatabaseSchemaInitializer;
import com.repairshop.saas.ticket.dto.BookingDraftDtos.UpsertRequest;
import com.repairshop.saas.ticket.exception.BookingDraftException;
import com.repairshop.saas.ticket.exception.GlobalExceptionHandler;
import com.repairshop.saas.ticket.service.BookingDraftService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Booking drafts end to end over MockMvc against an in-memory H2 database
 * (same plain-H2 mode as the dev profile). The booking_drafts table is created
 * by DatabaseSchemaInitializer's DDL helper — the same code that runs at boot.
 */
class BookingDraftControllerTest {

    private static final String DOT = "•";

    private final UUID shopA = UUID.randomUUID();
    private final UUID shopB = UUID.randomUUID();
    private final UUID ownerA = UUID.randomUUID();
    private final UUID ownerB = UUID.randomUUID();

    private JdbcTemplate jdbc;
    private ObjectMapper mapper;
    private BookingDraftService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:drafts-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        assertTrue(DatabaseSchemaInitializer.ensureBookingDraftsTable(jdbc));
        assertTrue(DatabaseSchemaInitializer.ensureBookingDraftsTable(jdbc), "DDL must be idempotent");
        jdbc.execute("CREATE TABLE tickets (id UUID PRIMARY KEY, shop_id UUID NOT NULL)");
        mapper = Jackson2ObjectMapperBuilder.json().build();
        service = new BookingDraftService(jdbc, mapper);
        mvc = MockMvcBuilders.standaloneSetup(new BookingDraftController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("SHUTDOWN");
    }

    // ------------------------------------------------------------------ helpers

    private static RequestPostProcessor as(UUID shopId, UUID userId, String... roles) {
        return req -> {
            req.setAttribute("shopId", shopId.toString());
            req.setAttribute("userId", userId.toString());
            req.setAttribute("roles", List.of(roles));
            return req;
        };
    }

    private RequestPostProcessor ownerA() {
        return as(shopA, ownerA, "SHOP_OWNER");
    }

    private RequestPostProcessor ownerB() {
        return as(shopB, ownerB, "SHOP_OWNER");
    }

    private Map<String, Object> body(Integer baseVersion, String customerName, String mobile) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("baseVersion", baseVersion);
        b.put("currentStep", "device");
        b.put("stepLabel", "Device details");
        b.put("customerName", customerName);
        b.put("customerMobile", mobile);
        b.put("deviceSummary", "iPhone 13 - screen");
        b.put("deviceCount", 1);
        b.put("payload", Map.of("customer", Map.of("name", String.valueOf(customerName)),
                "devices", List.of(Map.of("brand", "Apple", "issues", List.of("screen")))));
        return b;
    }

    private ResultActions putDraft(UUID id, Object body, RequestPostProcessor who) throws Exception {
        return mvc.perform(put("/booking-drafts/" + id).with(who)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    private UUID createDraft(RequestPostProcessor who, String name, String mobile) throws Exception {
        UUID id = UUID.randomUUID();
        putDraft(id, body(null, name, mobile), who).andExpect(status().isCreated());
        return id;
    }

    private ResultActions submit(UUID id, List<UUID> ticketIds, RequestPostProcessor who) throws Exception {
        return mvc.perform(post("/booking-drafts/" + id + "/submit").with(who)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("ticketIds", ticketIds))));
    }

    private UUID ticket(UUID shopId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tickets (id, shop_id) VALUES (?, ?)", id, shopId);
        return id;
    }

    private String statusOf(UUID id) {
        return jdbc.queryForObject("SELECT status FROM booking_drafts WHERE id = ?", String.class, id);
    }

    // ------------------------------------------------------------------ upsert

    @Test
    void upsertCreatesThenUpdatesThenRejectsStaleBaseVersion() throws Exception {
        UUID id = UUID.randomUUID();
        putDraft(id, body(null, "Ravi", "9876543210"), ownerA())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));

        putDraft(id, body(1, "Ravi Kumar", "9876543210"), ownerA())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        putDraft(id, body(1, "Stale tab", "9876543210"), ownerA())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.currentVersion").value(2))
                .andExpect(jsonPath("$.message", notNullValue()));

        // Creating over an existing draft (baseVersion null/0) is a conflict too, not an overwrite.
        putDraft(id, body(null, "Other device", "1"), ownerA())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentVersion").value(2));

        assertEquals("Ravi Kumar", jdbc.queryForObject(
                "SELECT customer_name FROM booking_drafts WHERE id = ?", String.class, id));
        assertEquals(ownerA, jdbc.queryForObject(
                "SELECT updated_by FROM booking_drafts WHERE id = ?", UUID.class, id));
    }

    @Test
    void upsertOfUnknownDraftWithNonZeroBaseVersionConflicts() throws Exception {
        putDraft(UUID.randomUUID(), body(3, "Ravi", null), ownerA())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.currentVersion").value(0));
        UUID id = UUID.randomUUID();
        putDraft(id, body(0, "Ravi", null), ownerA()).andExpect(status().isCreated());
    }

    @Test
    void concurrentSavesOnTheSameBaseVersionHaveExactlyOneWinner() throws Exception {
        UUID id = createDraft(ownerA(), "Ravi", null);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            UpsertRequest req = UpsertRequest.builder().baseVersion(1)
                    .payload(mapper.createObjectNode().put("n", i)).build();
            Callable<Boolean> save = () -> {
                start.await();
                try {
                    service.upsert(shopA, ownerA, id, req);
                    return true;
                } catch (BookingDraftException e) {
                    assertEquals("VERSION_CONFLICT", e.getCode());
                    return false;
                }
            };
            results.add(pool.submit(save));
        }
        start.countDown();
        int winners = 0;
        for (Future<Boolean> f : results) if (f.get()) winners++;
        pool.shutdown();
        assertEquals(1, winners);
        assertEquals(2, jdbc.queryForObject("SELECT version FROM booking_drafts WHERE id = ?", Integer.class, id));
    }

    @Test
    void upsertValidatesPayloadAndFields() throws Exception {
        Map<String, Object> noPayload = body(null, "Ravi", null);
        noPayload.remove("payload");
        putDraft(UUID.randomUUID(), noPayload, ownerA())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));

        Map<String, Object> arrayPayload = body(null, "Ravi", null);
        arrayPayload.put("payload", List.of(1, 2));
        putDraft(UUID.randomUUID(), arrayPayload, ownerA())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));

        Map<String, Object> negative = body(null, "Ravi", null);
        negative.put("deviceCount", -1);
        putDraft(UUID.randomUUID(), negative, ownerA())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DEVICE_COUNT"));

        Map<String, Object> huge = body(null, "Ravi", null);
        huge.put("payload", Map.of("notes", "x".repeat(1_000_001)));
        putDraft(UUID.randomUUID(), huge, ownerA())
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.message", notNullValue()));

        Map<String, Object> messy = body(null, "  " + "N".repeat(200) + "  ", "  98765 43210  ");
        messy.put("stepLabel", "   ");
        UUID id = UUID.randomUUID();
        putDraft(id, messy, ownerA()).andExpect(status().isCreated());
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT customer_name, customer_mobile, step_label FROM booking_drafts WHERE id = ?", id);
        assertEquals("N".repeat(160), row.get("CUSTOMER_NAME"));
        assertEquals("98765 43210", row.get("CUSTOMER_MOBILE"));
        assertEquals(null, row.get("STEP_LABEL"));

        mvc.perform(put("/booking-drafts/" + UUID.randomUUID()).with(ownerA())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deviceCount\":\"many\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BODY"));
        mvc.perform(get("/booking-drafts/not-a-uuid").with(ownerA()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void closedDraftsRejectSaves() throws Exception {
        UUID discarded = createDraft(ownerA(), "Ravi", null);
        mvc.perform(delete("/booking-drafts/" + discarded).with(ownerA())).andExpect(status().isNoContent());
        putDraft(discarded, body(1, "Ravi", null), ownerA())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRAFT_CLOSED"))
                .andExpect(jsonPath("$.draftStatus").value("DISCARDED"));

        UUID submitted = createDraft(ownerA(), "Asha", null);
        submit(submitted, List.of(ticket(shopA)), ownerA()).andExpect(status().isOk());
        putDraft(submitted, body(1, "Asha", null), ownerA())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRAFT_CLOSED"))
                .andExpect(jsonPath("$.draftStatus").value("SUBMITTED"));
    }

    // ------------------------------------------------------------------ isolation & auth

    @Test
    void draftsOfAnotherShopAreNotFound() throws Exception {
        UUID id = createDraft(ownerA(), "Ravi", "9876543210");

        mvc.perform(get("/booking-drafts/" + id).with(ownerB()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRAFT_NOT_FOUND"));
        putDraft(id, body(1, "Hijack", null), ownerB()).andExpect(status().isNotFound());
        putDraft(id, body(null, "Hijack", null), ownerB()).andExpect(status().isNotFound());
        mvc.perform(delete("/booking-drafts/" + id).with(ownerB())).andExpect(status().isNotFound());
        submit(id, List.of(ticket(shopB)), ownerB()).andExpect(status().isNotFound());
        mvc.perform(get("/booking-drafts").with(ownerB()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/booking-drafts/" + id).with(ownerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Ravi"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TECHNICIAN", "STAFF", "PICKUP_PERSON"})
    void employeesAreForbidden(String role) throws Exception {
        UUID id = createDraft(ownerA(), "Ravi", null);
        RequestPostProcessor employee = as(shopA, UUID.randomUUID(), role);
        mvc.perform(get("/booking-drafts").with(employee))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/booking-drafts/" + id).with(employee)).andExpect(status().isForbidden());
        putDraft(id, body(1, "x", null), employee).andExpect(status().isForbidden());
        mvc.perform(delete("/booking-drafts/" + id).with(employee)).andExpect(status().isForbidden());
        submit(id, List.of(ticket(shopA)), employee).andExpect(status().isForbidden());
        assertEquals("ACTIVE", statusOf(id));
    }

    @Test
    void superAdminAndOwnerWithExtraRolesAreAllowed() throws Exception {
        mvc.perform(get("/booking-drafts").with(as(shopA, UUID.randomUUID(), "SUPER_ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/booking-drafts").with(as(shopA, UUID.randomUUID(), "TECHNICIAN", "SHOP_OWNER")))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ reads

    @Test
    void listMasksMobileOmitsPayloadAndSortsNewestFirst() throws Exception {
        UUID older = createDraft(ownerA(), "Older", "+91 98765 43210");
        UUID newer = createDraft(ownerA(), "Newer", "9876543210");
        jdbc.update("UPDATE booking_drafts SET updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")), older);
        UUID otherStatus = createDraft(ownerA(), "Gone", null);
        mvc.perform(delete("/booking-drafts/" + otherStatus).with(ownerA())).andExpect(status().isNoContent());

        mvc.perform(get("/booking-drafts").with(ownerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", contains(newer.toString(), older.toString())))
                .andExpect(jsonPath("$.content[0].customerMobileMasked").value(DOT.repeat(6) + "3210"))
                .andExpect(jsonPath("$.content[1].customerMobileMasked").value(DOT.repeat(8) + "3210"))
                .andExpect(jsonPath("$.content[0].payload").doesNotExist())
                .andExpect(jsonPath("$.content[0].customerMobile").doesNotExist())
                .andExpect(jsonPath("$.content[0].ticketIds").doesNotExist())
                .andExpect(jsonPath("$.content[0].customerName").value("Newer"))
                .andExpect(jsonPath("$.content[0].currentStep").value("device"))
                .andExpect(jsonPath("$.content[0].stepLabel").value("Device details"))
                .andExpect(jsonPath("$.content[0].deviceSummary").value("iPhone 13 - screen"))
                .andExpect(jsonPath("$.content[0].deviceCount").value(1))
                .andExpect(jsonPath("$.content[0].version").value(1))
                .andExpect(jsonPath("$.content[0].createdBy").value(ownerA.toString()))
                .andExpect(jsonPath("$.content[0].createdAt", notNullValue()))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));

        mvc.perform(get("/booking-drafts?status=discarded&size=500").with(ownerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content[*].id", contains(otherStatus.toString())));
        mvc.perform(get("/booking-drafts?page=1&size=1").with(ownerA()))
                .andExpect(jsonPath("$.content[*].id", contains(older.toString())))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/booking-drafts?status=BOGUS").with(ownerA()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test
    void getReturnsFullDraftWithParsedPayload() throws Exception {
        UUID id = createDraft(ownerA(), "Ravi", "9876543210");
        String json = mvc.perform(get("/booking-drafts/" + id).with(ownerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerMobile").value("9876543210"))
                .andExpect(jsonPath("$.customerMobileMasked").value(DOT.repeat(6) + "3210"))
                .andExpect(jsonPath("$.payload.customer.name").value("Ravi"))
                .andExpect(jsonPath("$.payload.devices[0].issues[0]").value("screen"))
                .andExpect(jsonPath("$.ticketIds", hasSize(0)))
                .andExpect(jsonPath("$.submittedAt", nullValue()))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(json);
        assertTrue(node.get("payload").isObject(), "payload must be a JSON object, not a string");
        for (String field : List.of("id", "status", "currentStep", "stepLabel", "customerName", "customerMobileMasked",
                "customerMobile", "deviceSummary", "deviceCount", "version", "createdBy", "createdAt", "updatedAt",
                "submittedAt", "payload", "ticketIds")) {
            assertTrue(node.has(field), "missing field " + field);
        }
    }

    // ------------------------------------------------------------------ discard / submit

    @Test
    void discardIsIdempotentAndKeepsTheRow() throws Exception {
        UUID id = createDraft(ownerA(), "Ravi", null);
        mvc.perform(delete("/booking-drafts/" + id).with(ownerA())).andExpect(status().isNoContent());
        mvc.perform(delete("/booking-drafts/" + id).with(ownerA())).andExpect(status().isNoContent());
        assertEquals("DISCARDED", statusOf(id));
        mvc.perform(get("/booking-drafts/" + id).with(ownerA()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISCARDED"));
        mvc.perform(delete("/booking-drafts/" + UUID.randomUUID()).with(ownerA())).andExpect(status().isNotFound());
        submit(id, List.of(ticket(shopA)), ownerA())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRAFT_CLOSED"));
    }

    @Test
    void submitIsIdempotentAndRejectsForeignTickets() throws Exception {
        UUID id = createDraft(ownerA(), "Ravi", null);
        UUID t1 = ticket(shopA);
        UUID t2 = ticket(shopA);
        UUID foreign = ticket(shopB);

        submit(id, List.of(t1, foreign), ownerA())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TICKET_IDS"))
                .andExpect(jsonPath("$.invalidTicketIds", contains(foreign.toString())));
        submit(id, List.of(t1, UUID.randomUUID()), ownerA()).andExpect(status().isBadRequest());
        submit(id, List.of(), ownerA())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TICKET_IDS"));
        assertEquals("ACTIVE", statusOf(id));

        String first = submit(id, List.of(t1, t2), ownerA())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.ticketIds", contains(t1.toString(), t2.toString())))
                .andExpect(jsonPath("$.submittedAt", notNullValue()))
                .andExpect(jsonPath("$.payload.customer.name").value("Ravi"))
                .andReturn().getResponse().getContentAsString();

        String again = submit(id, List.of(t2, t1), ownerA())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketIds", containsInAnyOrder(t1.toString(), t2.toString())))
                .andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(first), mapper.readTree(again));

        submit(id, List.of(t1), ownerA())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRAFT_CLOSED"))
                .andExpect(jsonPath("$.draftStatus").value("SUBMITTED"));
        mvc.perform(delete("/booking-drafts/" + id).with(ownerA()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRAFT_CLOSED"));
        mvc.perform(get("/booking-drafts?status=SUBMITTED").with(ownerA()))
                .andExpect(jsonPath("$.content[*].id", contains(id.toString())));
    }

    // ------------------------------------------------------------------ availability

    @Test
    void missingTableAnswers503() throws Exception {
        jdbc.execute("DROP TABLE booking_drafts");
        mvc.perform(get("/booking-drafts").with(ownerA()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(BookingDraftController.UNAVAILABLE_MESSAGE))
                .andExpect(jsonPath("$.code").value("DRAFTS_UNAVAILABLE"));
        ObjectNode payload = mapper.createObjectNode().put("a", 1);
        putDraft(UUID.randomUUID(), Map.of("payload", payload), ownerA())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Drafts are temporarily unavailable."));
        mvc.perform(delete("/booking-drafts/" + UUID.randomUUID()).with(ownerA()))
                .andExpect(status().isServiceUnavailable());

        // The boot-time helper brings it back.
        assertTrue(DatabaseSchemaInitializer.ensureBookingDraftsTable(jdbc));
        mvc.perform(get("/booking-drafts").with(ownerA())).andExpect(status().isOk());
    }
}
