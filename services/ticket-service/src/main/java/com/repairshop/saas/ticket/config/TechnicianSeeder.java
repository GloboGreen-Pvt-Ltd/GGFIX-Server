package com.repairshop.saas.ticket.config;

import com.repairshop.saas.ticket.entity.Technician;
import com.repairshop.saas.ticket.repository.TechnicianRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Seeds two sample employees (technicians) for the E2E shop so Employee Management list is non-empty.
 * Also seeds the E2E login technician (technician / test) so GET /technicians/me works and tickets can be assigned.
 */
@Component
@Profile("dev")
@Order(2)
@RequiredArgsConstructor
public class TechnicianSeeder implements CommandLineRunner {

    private static final UUID E2E_ADMIN_SHOP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    /** Auth-service E2E technician user id (login: technician, password: test). */
    private static final UUID E2E_TECH_USER_ID = UUID.fromString("a992a80e-edf9-404e-8a4d-b8d344191cc5");

    private final TechnicianRepository technicianRepository;

    @Override
    @Transactional
    public void run(String... args) {
        // E2E technician profile so /technicians/me works and tickets can be assigned to him
        if (technicianRepository.findByShopIdAndUserId(E2E_ADMIN_SHOP_ID, E2E_TECH_USER_ID).isEmpty()) {
            technicianRepository.save(Technician.builder()
                    .shopId(E2E_ADMIN_SHOP_ID)
                    .userId(E2E_TECH_USER_ID)
                    .name("E2E Technician")
                    .email("technician")
                    .phone("+91 9876500000")
                    .roleLabel("TECHNICIAN")
                    .isAvailable(true)
                    .build());
        }

        if (technicianRepository.findByShopIdOrderByNameAsc(E2E_ADMIN_SHOP_ID).size() >= 3)
            return;

        if (technicianRepository.findByShopIdOrderByNameAsc(E2E_ADMIN_SHOP_ID).stream()
                .anyMatch(t -> "Siva S".equals(t.getName())))
            return;

        technicianRepository.save(Technician.builder()
                .shopId(E2E_ADMIN_SHOP_ID)
                .userId(null)
                .name("Siva S")
                .email("siva@shop.local")
                .phone("+91 896951 5914")
                .roleLabel("Junior Technician")
                .isAvailable(true)
                .build());

        technicianRepository.save(Technician.builder()
                .shopId(E2E_ADMIN_SHOP_ID)
                .userId(null)
                .name("Balaji")
                .email("balaji@shop.local")
                .phone("+91 896951 5915")
                .roleLabel("Technician")
                .isAvailable(true)
                .build());
    }
}
