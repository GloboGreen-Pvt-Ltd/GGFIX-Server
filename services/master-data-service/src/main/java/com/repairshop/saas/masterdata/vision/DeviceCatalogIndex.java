package com.repairshop.saas.masterdata.vision;

import com.repairshop.saas.masterdata.entity.MasterBrand;
import com.repairshop.saas.masterdata.entity.MasterDeviceCategory;
import com.repairshop.saas.masterdata.entity.MasterModel;
import com.repairshop.saas.masterdata.repository.MasterBrandRepository;
import com.repairshop.saas.masterdata.repository.MasterDeviceCategoryRepository;
import com.repairshop.saas.masterdata.repository.MasterModelRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The catalogue as the device matcher needs it — id, brand, category, name,
 * model numbers, image URL — cached for a few minutes. Every scan would
 * otherwise reload ~9,000 model rows (inline base64 images included) just to
 * keep a handful of strings from each.
 */
@Component
public class DeviceCatalogIndex {

    private static final Duration TTL = Duration.ofMinutes(10);

    public record Snapshot(List<DeviceMatcher.CatalogModel> models,
                           Map<UUID, String> brandNames,
                           Map<UUID, MasterDeviceCategory> categories) {}

    private final MasterModelRepository modelRepo;
    private final MasterBrandRepository brandRepo;
    private final MasterDeviceCategoryRepository categoryRepo;

    private volatile Snapshot snapshot;
    private volatile Instant loadedAt = Instant.EPOCH;

    public DeviceCatalogIndex(MasterModelRepository modelRepo,
                              MasterBrandRepository brandRepo,
                              MasterDeviceCategoryRepository categoryRepo) {
        this.modelRepo = modelRepo;
        this.brandRepo = brandRepo;
        this.categoryRepo = categoryRepo;
    }

    public Snapshot get() {
        Snapshot s = snapshot;
        if (s != null && Instant.now().isBefore(loadedAt.plus(TTL))) return s;
        synchronized (this) {
            if (snapshot != null && Instant.now().isBefore(loadedAt.plus(TTL))) return snapshot;
            snapshot = load();
            loadedAt = Instant.now();
            return snapshot;
        }
    }

    private Snapshot load() {
        List<DeviceMatcher.CatalogModel> models = modelRepo.findAll().stream()
                .map(DeviceCatalogIndex::slim)
                .toList();
        Map<UUID, String> brands = new HashMap<>();
        for (MasterBrand b : brandRepo.findAll()) {
            if (b.getName() != null) brands.put(b.getId(), b.getName());
        }
        Map<UUID, MasterDeviceCategory> categories = new HashMap<>();
        for (MasterDeviceCategory c : categoryRepo.findAll()) categories.put(c.getId(), c);
        // Unmodifiable HashMaps rather than Map.copyOf: callers look up by ids
        // that can be null, which Map.copyOf's get() rejects with an NPE.
        return new Snapshot(models, Collections.unmodifiableMap(brands), Collections.unmodifiableMap(categories));
    }

    private static DeviceMatcher.CatalogModel slim(MasterModel m) {
        String url = m.getImageUrl();
        return new DeviceMatcher.CatalogModel(
                m.getId(), m.getBrandId(), m.getCategoryId(), m.getSeriesId(), m.getName(),
                m.getModelNumber() == null ? List.of() : m.getModelNumber().stream().filter(Objects::nonNull).toList(),
                // Legacy inline data: URIs are dropped, same as the /master/models list.
                url != null && url.startsWith("data:") ? null : url);
    }
}
