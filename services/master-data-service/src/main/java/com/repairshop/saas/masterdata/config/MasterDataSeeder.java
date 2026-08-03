package com.repairshop.saas.masterdata.config;

import com.repairshop.saas.masterdata.entity.*;
import com.repairshop.saas.masterdata.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Seeds sample master data for in-memory (H2) dev DB when the database is empty.
 * Run with: spring.profiles.active=dev
 * Populates: brands (with imageBase64 for dropdowns), models/devices/spare parts (with imageBase64),
 *            RAM options, storage options, repair services.
 * Replace placeholder base64 with real brand logos / device images if desired.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class MasterDataSeeder implements CommandLineRunner {

    /** 8x8 grey PNG base64 (no data: prefix). */
    private static final String IMG_GREY = "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAIAQMAAAD+wSzIAAAABlBMVEX///+/v7+jQ3Y5AAAADklEQVQI12P4AIX8EAgALgAD/aNpbtEAAAAASUVORK5CYII";
    /** 10x10 red PNG base64. */
    private static final String IMG_RED = "iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAYAAACNMs+9AAAAFUlEQVR42mP8z8BQz0AEYBxVSF+FABJADveWkH6oAAAAASUVORK5CYII";
    /** 10x10 green PNG base64. */
    private static final String IMG_GREEN = "iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAYAAACNMs+9AAAAFUlEQVR42mNk+M9Qz0AEYBxVSF+FAAhKDveksOjmAAAAAElFTkSuQmCC";
    /** 10x10 blue PNG base64. */
    private static final String IMG_BLUE = "iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAYAAACNMs+9AAAAFUlEQVR42mNkYPhfz0AEYBxVSF+FAP5FDvcfRYWgAAAAAElFTkSuQmCC";
    /** Default for models/devices (visible in lists). */
    private static final String IMG_DEVICE = IMG_GREY;

    private final MasterBrandRepository brandRepo;
    private final MasterModelRepository modelRepo;
    private final MasterRamOptionRepository ramRepo;
    private final MasterStorageOptionRepository storageRepo;
    private final MasterRepairServiceRepository repairServiceRepo;

    @Override
    @Transactional
    public void run(String... args) {
        if (brandRepo.count() > 0)
            return;

        // ---- Brands (each with distinct base64 logo: grey/red/green/blue) ----
        MasterBrand apple = saveBrand("Apple", "https://dummyassets.local/brands/apple.png", IMG_GREY);
        MasterBrand samsung = saveBrand("Samsung", "https://dummyassets.local/brands/samsung.png", IMG_BLUE);
        MasterBrand google = saveBrand("Google", "https://dummyassets.local/brands/google.png", IMG_RED);
        MasterBrand oneplus = saveBrand("OnePlus", "https://dummyassets.local/brands/oneplus.png", IMG_RED);
        MasterBrand xiaomi = saveBrand("Xiaomi", "https://dummyassets.local/brands/xiaomi.png", IMG_GREEN);
        MasterBrand vivo = saveBrand("Vivo", "https://dummyassets.local/brands/vivo.png", IMG_BLUE);
        MasterBrand oppo = saveBrand("Oppo", "https://dummyassets.local/brands/oppo.png", IMG_GREEN);
        MasterBrand realme = saveBrand("Realme", "https://dummyassets.local/brands/realme.png", IMG_GREEN);
        MasterBrand motorola = saveBrand("Motorola", "https://dummyassets.local/brands/motorola.png", IMG_RED);

        // ---- Models (devices + spare parts; imageBase64 for dropdowns in mobile) ----
        saveDeviceModel(apple.getId(), "iPhone 15", "DEVICE", "https://dummyassets.local/models/iphone-15.png", IMG_DEVICE);
        saveDeviceModel(apple.getId(), "iPhone 14", "DEVICE", "https://dummyassets.local/models/iphone-14.png", IMG_DEVICE);
        saveDeviceModel(apple.getId(), "iPhone 15 Pro Max", "DEVICE", "https://dummyassets.local/models/iphone-15-pro-max.png", IMG_DEVICE);
        saveDeviceModel(apple.getId(), "iPhone 13", "DEVICE", "https://dummyassets.local/models/iphone-13.png", IMG_DEVICE);
        saveDeviceModel(samsung.getId(), "Galaxy Z Fold 7", "DEVICE", "https://dummyassets.local/models/galaxy-z-fold-7.png", IMG_DEVICE);
        saveDeviceModel(samsung.getId(), "Galaxy Z Fold 7 Display Combo", "SPARE_PART",
                "https://dummyassets.local/models/galaxy-z-fold-7-display-combo.png", IMG_DEVICE);
        saveDeviceModel(samsung.getId(), "Galaxy S24", "DEVICE", "https://dummyassets.local/models/galaxy-s24.png", IMG_DEVICE);
        saveDeviceModel(samsung.getId(), "Galaxy S24 Ultra", "DEVICE", "https://dummyassets.local/models/galaxy-s24-ultra.png", IMG_DEVICE);
        saveDeviceModel(samsung.getId(), "Galaxy A55", "DEVICE", "https://dummyassets.local/models/galaxy-a55.png", IMG_DEVICE);
        saveDeviceModel(google.getId(), "Pixel 8", "DEVICE", "https://dummyassets.local/models/pixel-8.png", IMG_DEVICE);
        saveDeviceModel(google.getId(), "Pixel 9 Pro", "DEVICE", "https://dummyassets.local/models/pixel-9-pro.png", IMG_DEVICE);
        saveDeviceModel(oneplus.getId(), "OnePlus 12", "DEVICE", "https://dummyassets.local/models/oneplus-12.png", IMG_DEVICE);
        saveDeviceModel(oneplus.getId(), "OnePlus Nord 4", "DEVICE", "https://dummyassets.local/models/oneplus-nord-4.png", IMG_DEVICE);
        saveDeviceModel(xiaomi.getId(), "Redmi Note 13", "DEVICE", "https://dummyassets.local/models/redmi-note-13.png", IMG_DEVICE);
        saveDeviceModel(xiaomi.getId(), "Redmi 13C", "DEVICE", "https://dummyassets.local/models/redmi-13c.png", IMG_DEVICE);
        saveDeviceModel(xiaomi.getId(), "Battery 5000mAh", "SPARE_PART", "https://dummyassets.local/models/battery.png", IMG_DEVICE);
        saveDeviceModel(xiaomi.getId(), "Charging Port Module", "SPARE_PART", "https://dummyassets.local/models/charging-port.png", IMG_DEVICE);
        saveDeviceModel(vivo.getId(), "Vivo Y200 5G", "DEVICE", "https://dummyassets.local/models/vivo-y200-5g.png", IMG_DEVICE);
        saveDeviceModel(vivo.getId(), "Vivo V30", "DEVICE", "https://dummyassets.local/models/vivo-v30.png", IMG_DEVICE);
        saveDeviceModel(oppo.getId(), "Oppo Reno 11", "DEVICE", "https://dummyassets.local/models/oppo-reno-11.png", IMG_DEVICE);
        saveDeviceModel(oppo.getId(), "Oppo A79 5G", "DEVICE", "https://dummyassets.local/models/oppo-a79.png", IMG_DEVICE);
        saveDeviceModel(realme.getId(), "Realme 12 Pro", "DEVICE", "https://dummyassets.local/models/realme-12-pro.png", IMG_DEVICE);
        saveDeviceModel(realme.getId(), "Realme C67", "DEVICE", "https://dummyassets.local/models/realme-c67.png", IMG_DEVICE);
        saveDeviceModel(motorola.getId(), "Moto G84", "DEVICE", "https://dummyassets.local/models/moto-g84.png", IMG_DEVICE);
        saveDeviceModel(motorola.getId(), "Moto Edge 50", "DEVICE", "https://dummyassets.local/models/moto-edge-50.png", IMG_DEVICE);

        // ---- RAM options ----
        saveRamOptions(List.of(2, 4, 6, 8, 12, 16), List.of("2 GB", "4 GB", "6 GB", "8 GB", "12 GB", "16 GB"));

        // ---- Storage options ----
        saveStorageOptions(List.of(32, 64, 128, 256, 512, 1024),
                List.of("32 GB", "64 GB", "128 GB", "256 GB", "512 GB", "1 TB"));

        // ---- Repair services ----
        saveRepairService("SCREEN", "Screen Repair", "Display or screen replacement");
        saveRepairService("BATTERY", "Battery Replacement", "Battery replacement and calibration");
        saveRepairService("WATER", "Water Damage", "Water damage cleaning and repair");
        saveRepairService("SPEAKER", "Speaker Repair", "Speaker or earpiece replacement");
        saveRepairService("CAMERA", "Camera Repair", "Front or rear camera replacement");
        saveRepairService("CHARGING", "Charging Port", "Charging port or dock connector repair");
        saveRepairService("SOFTWARE", "Software / OS", "OS reinstall, unlock, or software fix");
        saveRepairService("BACK_GLASS", "Back Glass", "Back glass or housing replacement");
    }

    private MasterBrand saveBrand(String name, String imageUrl, String imageBase64) {
        return brandRepo.save(MasterBrand.builder()
                .name(name)
                .imageUrl(imageUrl)
                .imageBase64(imageBase64)
                .build());
    }

    private void saveDeviceModel(UUID brandId, String name, String category, String imageUrl, String imageBase64) {
        modelRepo.save(MasterModel.builder()
                .brandId(brandId)
                .name(name)
                .category(category)
                .imageUrl(imageUrl)
                .imageBase64(imageBase64)
                .build());
    }

    private void saveRamOptions(List<Integer> valuesGb, List<String> labels) {
        for (int i = 0; i < valuesGb.size(); i++) {
            ramRepo.save(MasterRamOption.builder()
                    .valueGb(valuesGb.get(i))
                    .label(labels.get(i))
                    .build());
        }
    }

    private void saveStorageOptions(List<Integer> valuesGb, List<String> labels) {
        for (int i = 0; i < valuesGb.size(); i++) {
            storageRepo.save(MasterStorageOption.builder()
                    .valueGb(valuesGb.get(i))
                    .label(labels.get(i))
                    .build());
        }
    }

    private void saveRepairService(String code, String name, String description) {
        repairServiceRepo.save(MasterRepairService.builder()
                .code(code)
                .name(name)
                .description(description)
                .build());
    }
}
