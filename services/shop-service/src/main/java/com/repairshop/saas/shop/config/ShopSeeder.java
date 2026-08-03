package com.repairshop.saas.shop.config;

import com.repairshop.saas.shop.entity.Shop;
import com.repairshop.saas.shop.entity.ShopImage;
import com.repairshop.saas.shop.entity.ShopOfferedService;
import com.repairshop.saas.shop.entity.ShopPickupSlot;
import com.repairshop.saas.shop.repository.ShopImageRepository;
import com.repairshop.saas.shop.repository.ShopOfferedServiceRepository;
import com.repairshop.saas.shop.repository.ShopPickupSlotRepository;
import com.repairshop.saas.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Seeds the shop directory with the demo shops shown in the customer app
 * mockups (Cuddalore, Tamil Nadu area). Only runs when the shops table is
 * empty, so it is safe across restarts.
 */
@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class ShopSeeder implements CommandLineRunner {

    private final ShopRepository shopRepository;
    private final ShopOfferedServiceRepository offeredServiceRepository;
    private final ShopImageRepository shopImageRepository;
    private final ShopPickupSlotRepository pickupSlotRepository;

    private static final String DEFAULT_HOURS = "Monday - Saturday 09:30 AM to 09:00PM";
    private static final List<String> DEFAULT_SERVICES =
            List.of("REPAIR", "BUY", "SELL", "PICKUP");

    @Override
    @Transactional
    public void run(String... args) {
        if (shopRepository.count() > 0) {
            return;
        }
        log.info("Seeding shop directory with demo shops...");

        seedShop(
                "Globo Green Mobile Cuddalore",
                "globo-green-mobile-cuddalore",
                "globo.green@example.com",
                "+91 90000 11111",
                "No. 1, Bharathi Road, Manjakuppam, Cuddalore",
                "Cuddalore",
                "Tamil Nadu",
                "607001",
                new BigDecimal("11.7480000"),
                new BigDecimal("79.7714000"),
                new BigDecimal("4.6"),
                "Authorised Globo Green Mobile service centre for all major brands. Same-day display, battery and pickup-from-home repairs.",
                "https://dummyassets.local/shops/globo-green-cuddalore-hero.jpg",
                List.of(
                        "https://dummyassets.local/shops/globo-green-cuddalore-1.jpg",
                        "https://dummyassets.local/shops/globo-green-cuddalore-2.jpg"
                ));

        seedShop(
                "Sakthi Mobiles",
                "sakthi-mobiles",
                "sakthi.mobiles@example.com",
                "+91 90000 22222",
                "Manjakuppam Main Road, Cuddalore",
                "Cuddalore",
                "Tamil Nadu",
                "607001",
                new BigDecimal("11.7461000"),
                new BigDecimal("79.7695000"),
                new BigDecimal("4.3"),
                "Walk-in mobile repair and accessories store in the heart of Cuddalore.",
                "https://dummyassets.local/shops/sakthi-mobiles-hero.jpg",
                List.of("https://dummyassets.local/shops/sakthi-mobiles-1.jpg"));

        seedShop(
                "Jai Mobiles",
                "jai-mobiles",
                "jai.mobiles@example.com",
                "+91 90000 33333",
                "Thirupapuliyur Main Road, Cuddalore",
                "Cuddalore",
                "Tamil Nadu",
                "607002",
                new BigDecimal("11.7395000"),
                new BigDecimal("79.7728000"),
                new BigDecimal("4.1"),
                "Trusted neighbourhood mobile repair shop with quick turnaround on screens and batteries.",
                "https://dummyassets.local/shops/jai-mobiles-hero.jpg",
                List.of("https://dummyassets.local/shops/jai-mobiles-1.jpg"));

        seedShop(
                "Green Mobile",
                "green-mobile",
                "green.mobile@example.com",
                "+91 90000 44444",
                "Subbarayalu Street, Cuddalore",
                "Cuddalore",
                "Tamil Nadu",
                "607001",
                new BigDecimal("11.7512000"),
                new BigDecimal("79.7741000"),
                new BigDecimal("4.4"),
                "Buy, sell and repair mobiles with doorstep pickup across Cuddalore.",
                "https://dummyassets.local/shops/green-mobile-hero.jpg",
                List.of("https://dummyassets.local/shops/green-mobile-1.jpg"));

        log.info("Shop directory seeding complete: {} shops", shopRepository.count());
    }

    private void seedShop(String name, String slug, String email, String phone,
                          String address, String city, String state, String pincode,
                          BigDecimal latitude, BigDecimal longitude, BigDecimal rating,
                          String description, String heroImageUrl, List<String> galleryImages) {

        Shop shop = Shop.builder()
                .name(name)
                .slug(slug)
                .email(email)
                .address(address)
                .timezone("Asia/Kolkata")
                .isActive(Boolean.TRUE)
                .latitude(latitude)
                .longitude(longitude)
                .city(city)
                .state(state)
                .pincode(pincode)
                .rating(rating)
                .build();
        Shop saved = shopRepository.save(shop);
        UUID shopId = saved.getId();

        for (String code : DEFAULT_SERVICES) {
            offeredServiceRepository.save(ShopOfferedService.builder()
                    .shopId(shopId)
                    .serviceCode(code)
                    .isEnabled(Boolean.TRUE)
                    .build());
        }

        int order = 0;
        for (String url : galleryImages) {
            shopImageRepository.save(ShopImage.builder()
                    .shopId(shopId)
                    .imageUrl(url)
                    .sortOrder(order++)
                    .build());
        }

        pickupSlotRepository.save(ShopPickupSlot.builder()
                .shopId(shopId)
                .dayOfWeek(null) // any day
                .startTime(LocalTime.of(11, 0))
                .endTime(LocalTime.of(12, 0))
                .capacity(5)
                .build());
        pickupSlotRepository.save(ShopPickupSlot.builder()
                .shopId(shopId)
                .dayOfWeek(null)
                .startTime(LocalTime.of(15, 0))
                .endTime(LocalTime.of(18, 0))
                .capacity(10)
                .build());
    }
}
