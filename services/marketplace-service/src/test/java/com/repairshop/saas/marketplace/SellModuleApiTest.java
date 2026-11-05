package com.repairshop.saas.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end smoke test of the Sell module endpoints on an in-memory H2
 * database (H2 is already a dependency; schema from the entities). Verifies
 * wiring, repository queries, transactions and the contract's JSON shapes.
 * No real database is touched.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sellmodule;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "app.jwt.secret=" + SellModuleApiTest.SECRET
})
@AutoConfigureMockMvc
class SellModuleApiTest {

    static final String SECRET = "sell-module-test-secret-0123456789-abcdefghijklmnop";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private final String ownerA = token(UUID.randomUUID(), UUID.randomUUID(), "SHOP_OWNER", "OWNER");
    private final String ownerB = token(UUID.randomUUID(), UUID.randomUUID(), "SHOP_OWNER", "OWNER");
    private final String customer = token(UUID.randomUUID(), null, "CUSTOMER", null);

    private static String token(UUID userId, UUID shopId, String role, String loginScope) {
        var b = Jwts.builder().subject(userId.toString()).claim("roles", List.of(role));
        if (shopId != null) b.claim("shopId", shopId.toString());
        if (loginScope != null) b.claim("loginScope", loginScope);
        return "Bearer " + b.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private JsonNode call(MockHttpServletRequestBuilder req, String auth, Object body, int expected) throws Exception {
        req.header("Authorization", auth);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        MvcResult r = mvc.perform(req).andExpect(status().is(expected)).andReturn();
        String text = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return text.isBlank() ? null : json.readTree(text);
    }

    @Test
    void listingsEnquiriesAndBulkFlow() throws Exception {
        // ---- create + clientRef replay
        var create = new java.util.LinkedHashMap<String, Object>();
        create.put("title", "Original charger 20W");
        create.put("price", 1000);
        create.put("listingType", "ACCESSORY");
        create.put("itemCondition", "NEW");
        create.put("quantity", 2);
        create.put("fulfilmentOptions", List.of("PICKUP", "SHIPPING"));
        create.put("clientRef", "ref-1");
        create.put("status", "ACTIVE");
        JsonNode product = call(post("/shop/marketplace/products"), ownerA, create, 200);
        String id = product.get("id").asText();
        assertEquals("SHIPPING", product.get("fulfilmentOptions").get(1).asText());
        assertEquals(0, product.get("soldQuantity").asInt());

        mvc.perform(post("/shop/marketplace/products").header("Authorization", ownerA)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(id));

        // ---- shop list / summary / tenant isolation
        mvc.perform(get("/shop/marketplace/products").param("status", "ACTIVE").header("Authorization", ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.counts.ALL").value(1))
                .andExpect(jsonPath("$.counts.ACTIVE").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
        call(get("/shop/marketplace/products/" + id), ownerB, null, 404);
        call(put("/marketplace/products/" + id), ownerB, java.util.Map.of("price", 1), 403);
        call(delete("/marketplace/products/" + id), ownerB, null, 403);
        call(get("/shop/marketplace/summary"), customer, null, 403);

        // ---- public board hides drafts
        JsonNode draft = call(post("/shop/marketplace/products"), ownerA,
                java.util.Map.of("title", "Draft phone", "status", "DRAFT"), 200);
        mvc.perform(get("/marketplace/products").param("status", "DRAFT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        call(get("/marketplace/products/" + draft.get("id").asText()), customer, null, 404);
        call(patch("/shop/marketplace/products/" + draft.get("id").asText() + "/status"), ownerA,
                java.util.Map.of("status", "SOLD"), 409);

        // ---- enquiry -> accept -> confirm
        JsonNode enquiry = call(post("/customer/marketplace/products/" + id + "/enquiries"), customer,
                java.util.Map.of("kind", "PURCHASE_REQUEST", "offerAmount", 900, "message", "Can you do 900?"), 200);
        String eid = enquiry.get("id").asText();
        assertEquals("OPEN", enquiry.get("status").asText());
        assertEquals("BUYER", enquiry.get("lastOfferBy").asText());
        assertEquals(true, enquiry.has("chatThreadId"));

        mvc.perform(get("/shop/marketplace/enquiries").header("Authorization", ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].unread").value(true))
                .andExpect(jsonPath("$.content[0].offers").doesNotExist())
                .andExpect(jsonPath("$.counts.ACTIVE").value(1))
                .andExpect(jsonPath("$.counts.PURCHASE_REQUEST").value(1));
        mvc.perform(get("/shop/marketplace/summary").header("Authorization", ownerA))
                .andExpect(jsonPath("$.purchaseRequestsOpen").value(1))
                .andExpect(jsonPath("$.enquiriesUnread").value(1))
                .andExpect(jsonPath("$.active").value(1))
                .andExpect(jsonPath("$.draft").value(1));

        JsonNode detail = call(get("/shop/marketplace/enquiries/" + eid), ownerA, null, 200);
        assertEquals(false, detail.get("unread").asBoolean());
        assertEquals(1, detail.get("offers").size());
        call(get("/shop/marketplace/enquiries/" + eid), ownerB, null, 404);
        call(post("/customer/marketplace/enquiries/" + eid + "/accept"), customer, null, 409);

        JsonNode accepted = call(post("/shop/marketplace/enquiries/" + eid + "/accept"), ownerA, null, 200);
        assertEquals("ACCEPTED", accepted.get("status").asText());
        assertEquals(0, accepted.get("agreedAmount").decimalValue().compareTo(new java.math.BigDecimal("900")));

        JsonNode confirmed = call(post("/shop/marketplace/enquiries/" + eid + "/confirm"), ownerA,
                java.util.Map.of("fulfilment", "SHIPPING"), 200);
        assertEquals("CONFIRMED", confirmed.get("status").asText());
        assertEquals(1, confirmed.get("product").get("quantity").asInt());
        JsonNode after = call(get("/shop/marketplace/products/" + id), ownerA, null, 200);
        assertEquals(1, after.get("quantity").asInt());
        assertEquals(1, after.get("soldQuantity").asInt());

        mvc.perform(get("/customer/marketplace/enquiries").header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));

        // ---- soft delete
        call(delete("/shop/marketplace/products/" + id), ownerA, null, 204);
        call(get("/shop/marketplace/products/" + id), ownerA, null, 404);

        // ---- bulk: CREATED (draft, no photos) / EXISTING / FAILED
        var bulk = new java.util.LinkedHashMap<String, Object>();
        bulk.put("batchId", UUID.randomUUID().toString());
        bulk.put("publish", true);
        bulk.put("items", List.of(
                java.util.Map.of("clientRef", "b-1", "title", "Redmi Note 12", "price", 9000, "listingType", "DEVICE"),
                java.util.Map.of("clientRef", "b-1", "title", "Redmi Note 12", "price", 9000, "listingType", "DEVICE"),
                java.util.Map.of("clientRef", "b-2", "title", "Bad", "brandId", "not-a-uuid")));
        mvc.perform(post("/shop/marketplace/products/bulk").header("Authorization", ownerA)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bulk)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.existing").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.drafts").value(2))
                .andExpect(jsonPath("$.results[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.results[1].outcome").value("EXISTING"))
                .andExpect(jsonPath("$.results[2].errors[0]").value("brandId must be a valid UUID"));
    }

    @Test
    void listingPermissionsForRolesShopsAndLegacyEndpoints() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID shop1 = UUID.randomUUID();
        UUID shop2 = UUID.randomUUID(); // second shop of the same owner
        UUID otherShop = UUID.randomUUID();
        jdbc.execute("CREATE TABLE IF NOT EXISTS shops (id UUID PRIMARY KEY, owner_user_id UUID)");
        jdbc.update("INSERT INTO shops (id, owner_user_id) VALUES (?, ?), (?, ?), (?, ?)",
                shop1, owner, shop2, owner, otherShop, UUID.randomUUID());

        String ownerToken = token(owner, shop1, "SHOP_OWNER", "OWNER");
        String shopScoped = token(owner, shop1, "SHOP_OWNER", "SHOP");
        String technician = token(UUID.randomUUID(), shop1, "TECHNICIAN", null);
        String admin = token(UUID.randomUUID(), null, "SUPER_ADMIN", null);
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("title", "Legacy listing");
        body.put("price", 500);
        body.put("type", "SELL");

        // A token that merely carries a shopId is not a seller.
        call(get("/shop/marketplace/summary"), technician, null, 403);
        call(get("/shop/marketplace/enquiries"), technician, null, 403);
        body.put("shopId", shop1.toString());
        call(post("/marketplace/products"), technician, body, 403);
        call(post("/marketplace/products"), customer, body, 403);

        // Owner: active shop and another owned shop are fine; someone else's shop is not.
        JsonNode own = call(post("/marketplace/products"), ownerToken, body, 200);
        body.put("shopId", shop2.toString());
        JsonNode second = call(post("/marketplace/products"), ownerToken, body, 200);
        assertEquals(shop2.toString(), second.get("shopId").asText());
        body.put("shopId", otherShop.toString());
        call(post("/marketplace/products"), ownerToken, body, 403);
        // A shop-mobile login is limited to its own shop.
        body.put("shopId", shop2.toString());
        call(post("/marketplace/products"), shopScoped, body, 403);
        // Admin tooling keeps access.
        body.put("shopId", otherShop.toString());
        call(post("/marketplace/products"), admin, body, 200);

        // Legacy PUT / DELETE follow the same rule.
        String secondId = second.get("id").asText();
        call(put("/marketplace/products/" + secondId), ownerToken, java.util.Map.of("price", 450), 200);
        call(put("/marketplace/products/" + secondId), shopScoped, java.util.Map.of("price", 400), 403);
        call(put("/marketplace/products/" + secondId), customer, java.util.Map.of("price", 1), 403);
        call(put("/marketplace/products/" + secondId), ownerToken, java.util.Map.of("shopId", otherShop.toString()), 403);
        call(put("/marketplace/products/" + secondId), ownerToken, java.util.Map.of("status", "DELETED"), 400);
        call(delete("/marketplace/products/" + secondId), customer, null, 403);

        // Legacy DELETE is a soft delete: gone from every read, row kept.
        call(delete("/marketplace/products/" + secondId), ownerToken, null, 204);
        call(get("/marketplace/products/" + secondId), ownerToken, null, 404);
        call(put("/marketplace/products/" + secondId), ownerToken, java.util.Map.of("price", 1), 404);
        assertEquals("DELETED", jdbc.queryForObject(
                "SELECT status FROM marketplace_products WHERE id = ?", String.class, UUID.fromString(secondId)));
        call(get("/marketplace/products/" + own.get("id").asText()), customer, null, 200);
    }
}
