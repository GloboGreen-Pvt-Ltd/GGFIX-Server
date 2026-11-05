package com.repairshop.saas.marketplace.service;

import com.repairshop.saas.marketplace.dto.EnquiryResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Owner Dashboard bell rows (shop_notifications, migration 29) for marketplace
 * enquiries. Same shared database and same best-effort INSERT as ticket-service's
 * PickupBookingController.notifyShop; no cross-service call. type='sell_enquiry' puts the
 * row on the Partner app's Sell notification tab and opens Buyer Enquiries
 * (plain 'sell' is the customer Sell Requests feed).
 *
 * Call only AFTER the enquiry transaction has committed (the controllers do), so
 * a failed insert can never roll back or abort the enquiry change.
 */
@Component("marketplaceShopNotificationWriter") // named: common.notify has a same-named bean
@RequiredArgsConstructor
public class ShopNotificationWriter {

    private static final Logger log = LoggerFactory.getLogger(ShopNotificationWriter.class);

    private final JdbcTemplate jdbc;

    public void enquiryCreated(EnquiryResponse e) {
        boolean purchase = EnquiryService.PURCHASE_REQUEST.equals(e.getKind());
        String body = buyer(e) + (purchase ? " wants to buy " : " asked about ") + product(e)
                + (e.getLastOfferAmount() != null ? " and offered " + money(e.getLastOfferAmount()) : "") + ".";
        insert(e.getShopId(), purchase ? "MARKETPLACE_PURCHASE_REQUEST" : "MARKETPLACE_ENQUIRY",
                purchase ? "New purchase request" : "New enquiry", body);
    }

    public void counterOffer(EnquiryResponse e) {
        insert(e.getShopId(), "MARKETPLACE_ENQUIRY_COUNTERED", "New counter-offer",
                buyer(e) + " offered " + money(e.getLastOfferAmount()) + " for " + product(e) + ".");
    }

    public void offerAccepted(EnquiryResponse e) {
        insert(e.getShopId(), "MARKETPLACE_ENQUIRY_ACCEPTED", "Offer accepted",
                buyer(e) + " accepted " + money(e.getAgreedAmount()) + " for " + product(e)
                        + ". Confirm the sale to update your stock.");
    }

    private void insert(UUID shopId, String statusKey, String title, String body) {
        if (shopId == null) return;
        try {
            jdbc.update(
                    "INSERT INTO shop_notifications " +
                            "(id, shop_id, status_key, title, body, type, is_read, created_at) " +
                            "VALUES (CAST(? AS UUID), CAST(? AS UUID), ?, ?, ?, 'sell_enquiry', false, now())",
                    UUID.randomUUID().toString(), shopId.toString(), statusKey, title, body);
        } catch (DataAccessException ex) {
            log.warn("marketplace shop notification skipped for shop {}: {}", shopId, ex.getMessage());
        }
    }

    private static String buyer(EnquiryResponse e) {
        String name = e.getBuyer() != null ? e.getBuyer().getName() : null;
        return name == null || name.isBlank() ? "A buyer" : name.trim();
    }

    private static String product(EnquiryResponse e) {
        String title = e.getProduct() != null ? e.getProduct().getTitle() : null;
        return title == null || title.isBlank() ? "your listing" : "\"" + title.trim() + "\"";
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "an amount" : "₹" + amount.stripTrailingZeros().toPlainString();
    }
}
