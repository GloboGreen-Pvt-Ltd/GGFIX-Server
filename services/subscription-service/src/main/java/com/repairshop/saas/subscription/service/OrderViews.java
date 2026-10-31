package com.repairshop.saas.subscription.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repairshop.saas.subscription.dto.v2.V2Views.OrderView;
import com.repairshop.saas.subscription.dto.v2.V2Views.QuoteView;
import com.repairshop.saas.subscription.dto.v2.V2Views.ShopRef;
import com.repairshop.saas.subscription.entity.SubscriptionOrder;
import com.repairshop.saas.subscription.repository.SubscriptionPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Order entity → contract Order JSON, plus snapshot (de)serialisation. */
@Component
@RequiredArgsConstructor
public class OrderViews {

    private final ObjectMapper mapper;
    private final SubscriptionPaymentRepository payments;

    public OrderView of(SubscriptionOrder o) {
        if (o == null) return null;
        List<ShopRef> shops = shops(o.getShopsSnapshot());
        UUID paymentId = o.getRazorpayPaymentId() == null ? null
                : payments.findByRazorpayPaymentId(o.getRazorpayPaymentId()).map(p -> p.getId()).orElse(null);
        return new OrderView(
                o.getId(), o.getOrderNo(), o.getStatus(), o.getPlanCode(), o.getPlanName(), o.getDurationLabel(),
                o.getChannel(), shops.stream().map(ShopRef::shopId).toList(), shops, tree(o.getQuoteSnapshot()),
                o.getTotalPaise() == null ? 0L : o.getTotalPaise(), o.getRazorpayOrderId(),
                o.getRazorpayPaymentId(), o.getPaymentMethod(), o.getCreatedAt(), o.getPaidAt(),
                o.getActivatedAt(), o.getSubscriptionId(), o.getFailureReason(), paymentId, o.getPaymentMode());
    }

    public List<ShopRef> shops(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, new TypeReference<List<ShopRef>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    public QuoteView quote(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readValue(json, QuoteView.class);
        } catch (Exception e) {
            return null;
        }
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise snapshot", e);
        }
    }

    public JsonNode tree(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
