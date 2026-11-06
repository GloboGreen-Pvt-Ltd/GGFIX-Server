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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fixed-price Buy flow on in-memory H2: publish -> buy -> confirm -> assign -> pickup -> deliver, plus visit shop, failures, permissions, idempotency. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fixedprice;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "app.jwt.secret=" + FixedPriceOrderFlowTest.SECRET
})
@AutoConfigureMockMvc
class FixedPriceOrderFlowTest {

    static final String SECRET = "fixed-price-test-secret-0123456789-abcdefghijklmnop";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.junit.jupiter.api.BeforeEach
    void staffDirectory() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS users (id UUID PRIMARY KEY, shop_id UUID NOT NULL, is_active BOOLEAN)");
        jdbc.update("MERGE INTO users (id, shop_id, is_active) KEY (id) VALUES (?, ?, TRUE)", staffId, shopA);
        jdbc.update("MERGE INTO users (id, shop_id, is_active) KEY (id) VALUES (?, ?, TRUE)", foreignStaff, UUID.randomUUID());
    }

    private final UUID shopA = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID foreignStaff = UUID.randomUUID();
    private final String owner = token(UUID.randomUUID(), shopA, "SHOP_OWNER", "OWNER");
    private final String otherOwner = token(UUID.randomUUID(), UUID.randomUUID(), "SHOP_OWNER", "OWNER");
    private final String staff = token(staffId, shopA, "TECHNICIAN", null);
    private final String otherStaff = token(UUID.randomUUID(), shopA, "TECHNICIAN", null);
    private final String customer = token(UUID.randomUUID(), null, "CUSTOMER", null);
    private final String customer2 = token(UUID.randomUUID(), null, "CUSTOMER", null);

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

    private String publish(int qty, String fulfilment) throws Exception {
        Map<String, Object> p = new HashMap<>();
        p.put("title", "Apple iPhone 14 (128 GB)");
        p.put("price", 52999);
        p.put("status", "ACTIVE");
        p.put("listingType", "ACCESSORY");
        p.put("itemCondition", "NEW");
        p.put("quantity", qty);
        p.put("fulfilmentOptions", List.of(fulfilment));
        return call(post("/shop/marketplace/products"), owner, p, 200).get("id").asText();
    }

    private Map<String, Object> order(String productId, String fulfilment, String key) {
        Map<String, Object> b = new HashMap<>();
        b.put("productId", productId);
        b.put("fulfilmentType", fulfilment);
        b.put("idempotencyKey", key);
        b.put("expectedUnitPrice", 52999);
        b.put("deliveryAddress", "No.12, Gandhi Street, Chennai");
        return b;
    }

    @Test
    void homeDeliveryEndToEnd() throws Exception {
        String pid = publish(2, "SHIPPING");

        // price tamper rejected
        Map<String, Object> bad = order(pid, "HOME_DELIVERY", "k0");
        bad.put("expectedUnitPrice", 1);
        call(post("/customer/buy-orders"), customer, bad, 409);
        // online payment is not faked
        Map<String, Object> upi = order(pid, "HOME_DELIVERY", "k0");
        upi.put("paymentMethod", "UPI");
        call(post("/customer/buy-orders"), customer, upi, 400);

        JsonNode o = call(post("/customer/buy-orders"), customer, order(pid, "HOME_DELIVERY", "k1"), 200);
        String id = o.get("id").asText();
        String otp = o.get("handoverOtp").asText();
        assertEquals("PLACED", o.get("status").asText());
        assertEquals(0, new java.math.BigDecimal("52999").compareTo(o.get("totalAmount").decimalValue()));
        // idempotent replay returns same order, no extra stock taken
        assertEquals(id, call(post("/customer/buy-orders"), customer, order(pid, "HOME_DELIVERY", "k1"), 200).get("id").asText());
        assertEquals(1, call(get("/shop/marketplace/products/" + pid), owner, null, 200).get("soldQuantity").asInt());

        // permissions
        call(get("/customer/buy-orders/" + id), customer2, null, 404);
        call(post("/shop/buy-orders/" + id + "/confirm"), otherOwner, null, 404);
        call(post("/staff/buy-orders/" + id + "/accept"), staff, null, 404); // not assigned yet

        call(post("/shop/buy-orders/" + id + "/confirm"), owner, null, 200);
        // cross-shop employee cannot be assigned
        call(post("/shop/buy-orders/" + id + "/assign"), owner, Map.of("staffUserId", foreignStaff.toString()), 400);
        call(post("/shop/buy-orders/" + id + "/assign"), owner, Map.of("staffUserId", staffId.toString()), 200);
        call(post("/staff/buy-orders/" + id + "/accept"), otherStaff, null, 404);
        assertEquals(1, call(get("/staff/buy-orders"), staff, null, 200).size());
        call(post("/staff/buy-orders/" + id + "/accept"), staff, null, 200);
        call(post("/staff/buy-orders/" + id + "/pickup"), staff, Map.of("productVerified", false), 400);
        call(post("/staff/buy-orders/" + id + "/pickup"), staff,
                Map.of("productVerified", true, "imei", "123456789012345", "photos", List.of("https://cdn.neargfix.com/p1.jpg")), 400); // bad Luhn
        call(post("/staff/buy-orders/" + id + "/pickup"), staff,
                Map.of("productVerified", true, "imei", "490154203237518", "photos", List.of("https://cdn.neargfix.com/p1.jpg")), 200);
        JsonNode sv = call(get("/staff/buy-orders/" + id), staff, null, 200);
        assertEquals("490154203237518", sv.get("verifiedImei").asText());
        assertEquals(false, sv.has("handoverOtp")); // employee never sees the OTP
        assertEquals(true, sv.has("shopAddress") && sv.has("customerName"));
        call(post("/staff/buy-orders/" + id + "/start-delivery"), staff, null, 200);

        Map<String, Object> deliver = new HashMap<>(Map.of("amountCollected", 52999, "otp", "XXXX",
                "photos", List.of("https://cdn.neargfix.com/d1.jpg")));
        call(post("/staff/buy-orders/" + id + "/deliver"), staff, deliver, 400); // wrong OTP
        deliver.put("otp", otp);
        deliver.put("amountCollected", 100);
        call(post("/staff/buy-orders/" + id + "/deliver"), staff, deliver, 400); // short payment
        deliver.put("amountCollected", 52999);
        assertEquals("DELIVERED", call(post("/staff/buy-orders/" + id + "/deliver"), staff, deliver, 200).get("status").asText());
        call(post("/staff/buy-orders/" + id + "/deliver"), staff, deliver, 200); // idempotent

        JsonNode c = call(get("/customer/buy-orders/" + id), customer, null, 200);
        assertEquals("PAID", c.get("paymentStatus").asText());
        assertEquals(7, c.get("events").size());
        assertEquals("DELIVERED", call(get("/shop/buy-orders/" + id), owner, null, 200).get("status").asText());
        call(post("/customer/buy-orders/" + id + "/cancel"), customer, null, 409);
    }

    @Test
    void stockLimitFailedDeliveryAndReturn() throws Exception {
        String pid = publish(1, "SHIPPING");
        String id = call(post("/customer/buy-orders"), customer, order(pid, "HOME_DELIVERY", "a"), 200).get("id").asText();
        call(post("/customer/buy-orders"), customer2, order(pid, "HOME_DELIVERY", "b"), 409); // sold out
        assertEquals("SOLD", call(get("/shop/marketplace/products/" + pid), owner, null, 200).get("status").asText());

        call(post("/shop/buy-orders/" + id + "/confirm"), owner, null, 200);
        call(post("/shop/buy-orders/" + id + "/assign"), owner, Map.of("staffUserId", staffId.toString()), 200);
        call(post("/staff/buy-orders/" + id + "/decline"), staff, null, 200);
        call(post("/shop/buy-orders/" + id + "/assign"), owner, Map.of("staffUserId", staffId.toString()), 200); // reassign
        call(post("/staff/buy-orders/" + id + "/accept"), staff, null, 200);
        call(post("/staff/buy-orders/" + id + "/fail"), staff, Map.of(), 400);
        call(post("/staff/buy-orders/" + id + "/fail"), staff, Map.of("reason", "Customer not reachable"), 200);
        call(post("/shop/buy-orders/" + id + "/return-received"), owner, null, 200);
        JsonNode p = call(get("/shop/marketplace/products/" + pid), owner, null, 200);
        assertEquals(0, p.get("soldQuantity").asInt());
        assertEquals("ACTIVE", p.get("status").asText());
    }

    @Test
    void visitShopNeedsInStorePaymentAndOtp() throws Exception {
        String pid = publish(3, "PICKUP");
        call(post("/customer/buy-orders"), customer, order(pid, "HOME_DELIVERY", "h"), 409); // no delivery
        JsonNode o = call(post("/customer/buy-orders"), customer, order(pid, "VISIT_SHOP", "v"), 200);
        String id = o.get("id").asText();
        call(post("/shop/buy-orders/" + id + "/assign"), owner, Map.of("staffUserId", staffId.toString()), 409);
        call(post("/shop/buy-orders/" + id + "/handover"), owner, Map.of("amountCollected", 52999, "otp", o.get("handoverOtp").asText()), 409);
        call(post("/shop/buy-orders/" + id + "/confirm"), owner, null, 200);
        call(post("/shop/buy-orders/" + id + "/handover"), owner, Map.of("amountCollected", 52999, "otp", "0000x"), 400);
        assertEquals("COMPLETED", call(post("/shop/buy-orders/" + id + "/handover"), owner,
                Map.of("amountCollected", 52999, "otp", o.get("handoverOtp").asText(), "paymentRef", "CASH"), 200).get("status").asText());
        assertEquals(1, call(get("/shop/buy-orders"), owner, null, 200).size());

        // reject releases stock
        String id2 = call(post("/customer/buy-orders"), customer2, order(pid, "VISIT_SHOP", "v2"), 200).get("id").asText();
        call(post("/shop/buy-orders/" + id2 + "/reject"), owner, Map.of("reason", "Out of stock"), 200);
        assertEquals(1, call(get("/shop/marketplace/products/" + pid), owner, null, 200).get("soldQuantity").asInt());
    }
}
