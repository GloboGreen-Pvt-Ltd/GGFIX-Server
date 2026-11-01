package com.repairshop.saas.ticket.whatsapp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Everything a booking confirmation shows, read fresh from tickets + shops when the message is built. */
public record BookingSnapshot(
        String bookingNumber,
        String shopName,
        String shopPhone,
        String shopAddress,
        String customerName,
        String customerPhone,
        Instant bookedAt,
        List<Device> devices) {

    public record Device(
            String trackingId,
            String name,
            String imageUrl,
            String servicesSummary,
            List<PriceItem> items,
            String accessories,
            BigDecimal estimate,
            BigDecimal paid,
            BigDecimal balance,
            Instant deliveryAt) {}

    public record PriceItem(String label, BigDecimal amount, String warranty) {}

    public BigDecimal total() {
        return devices.stream().map(Device::estimate).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal paid() {
        return devices.stream().map(Device::paid).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Stored balance when every device has one, else total − paid. */
    public BigDecimal balance() {
        boolean allStored = !devices.isEmpty() && devices.stream().allMatch(d -> d.balance() != null);
        if (allStored) return devices.stream().map(Device::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        return total().subtract(paid()).max(BigDecimal.ZERO);
    }

    /** Latest promised delivery across the booking's devices; null when none is set. */
    public Instant latestDelivery() {
        return devices.stream().map(Device::deliveryAt).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
    }
}
