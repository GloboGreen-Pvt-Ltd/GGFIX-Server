package com.repairshop.saas.masterdata.controller;

import com.repairshop.saas.masterdata.dto.*;
import com.repairshop.saas.masterdata.entity.*;
import com.repairshop.saas.masterdata.repository.*;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/master")
public class MasterDataController {

    private final MasterBrandRepository brandRepo;
    private final MasterModelRepository modelRepo;
    private final MasterRamOptionRepository ramRepo;
    private final MasterStorageOptionRepository storageRepo;
    private final MasterRepairServiceRepository repairServiceRepo;
    private final MasterRepairCategoryRepository repairCategoryRepo;
    private final JdbcTemplate jdbc;

    public MasterDataController(MasterBrandRepository brandRepo, MasterModelRepository modelRepo,
                                 MasterRamOptionRepository ramRepo, MasterStorageOptionRepository storageRepo,
                                 MasterRepairServiceRepository repairServiceRepo,
                                 MasterRepairCategoryRepository repairCategoryRepo,
                                 JdbcTemplate jdbc) {
        this.brandRepo = brandRepo;
        this.modelRepo = modelRepo;
        this.ramRepo = ramRepo;
        this.storageRepo = storageRepo;
        this.repairServiceRepo = repairServiceRepo;
        this.repairCategoryRepo = repairCategoryRepo;
        this.jdbc = jdbc;
    }

    // ---- Brands ----
    @GetMapping("/brands")
    public ResponseEntity<List<MasterBrand>> getBrands() {
        return ResponseEntity.ok(brandRepo.findAll());
    }

    @PostMapping("/brands")
    public ResponseEntity<MasterBrand> createBrand(@RequestBody BrandRequest req) {
        MasterBrand e = MasterBrand.builder()
                .name(req.getName())
                .imageUrl(req.getImageUrl())
                .imageBase64(req.getImageBase64())
                .build();
        return ResponseEntity.ok(brandRepo.save(e));
    }

    @PutMapping("/brands/{id}")
    public ResponseEntity<MasterBrand> updateBrand(@PathVariable UUID id, @RequestBody BrandRequest req) {
        return brandRepo.findById(id)
                .map(e -> {
                    e.setName(req.getName());
                    e.setImageUrl(req.getImageUrl());
                    if (req.getImageBase64() != null) e.setImageBase64(req.getImageBase64());
                    return ResponseEntity.ok(brandRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/brands/{id}")
    public ResponseEntity<Void> deleteBrand(@PathVariable UUID id) {
        if (!brandRepo.existsById(id)) return ResponseEntity.notFound().build();
        brandRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Models ----

    /**
     * Every model, in one request, without the base64 weight.
     *
     * The admin previously assembled this list with one call per brand — 55
     * sequential round-trips — because a full listing had once exhausted the heap.
     * ModelListItem drops the inline data: URIs that accounted for 82% of the bytes,
     * so the whole catalogue now fits comfortably in a single response.
     *
     * Optional filters keep the existing per-brand and per-series views working off
     * the same endpoint.
     */
    @GetMapping("/models")
    public ResponseEntity<List<ModelListItem>> listModels(
            @RequestParam(value = "brandId", required = false) UUID brandId,
            @RequestParam(value = "seriesId", required = false) UUID seriesId) {
        List<MasterModel> rows;
        if (seriesId != null) {
            rows = modelRepo.findBySeriesIdOrderByName(seriesId);
        } else if (brandId != null) {
            rows = modelRepo.findByBrandIdOrderByName(brandId);
        } else {
            rows = modelRepo.findAll(org.springframework.data.domain.Sort.by("name"));
        }
        return ResponseEntity.ok(rows.stream().map(ModelListItem::from).toList());
    }

    @GetMapping("/brands/{brandId}/models")
    public ResponseEntity<List<MasterModel>> getModelsByBrand(@PathVariable UUID brandId) {
        return ResponseEntity.ok(modelRepo.findByBrandIdOrderByName(brandId));
    }

    /**
     * Resolve a device's raw Build.MODEL code (e.g. "RMX3999") — or a plain
     * model name — to its catalog model via master_models.model_number (jsonb).
     * Match each stored code by its prefix-before-space/paren, then fall back to
     * the display name. Used by the customer app Home "Sell This Device" card.
     */
    @GetMapping("/models/by-number")
    public ResponseEntity<Map<String, Object>> getModelByNumber(@RequestParam("number") String number) {
        if (number == null || number.isBlank()) return ResponseEntity.notFound().build();
        final String num = number.trim();
        final String sql =
                "SELECT id, name, image_url, image_base64 FROM master_models " +
                "WHERE EXISTS (SELECT 1 FROM jsonb_array_elements_text(" +
                "  CASE WHEN jsonb_typeof(model_number) = 'array' THEN model_number ELSE '[]'::jsonb END) e " +
                "  WHERE lower((regexp_match(trim(e), '^[^ (]+'))[1]) = lower(?)) " +
                "OR lower(name) = lower(?) LIMIT 1";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, num, num);
        if (rows.isEmpty()) return ResponseEntity.notFound().build();
        Map<String, Object> r = rows.get(0);
        Map<String, Object> out = new HashMap<>();
        out.put("id", r.get("id"));
        out.put("name", r.get("name"));
        out.put("imageUrl", r.get("image_url"));
        out.put("imageBase64", r.get("image_base64"));
        return ResponseEntity.ok(out);
    }

    /** Single model, incl. its inline colors + ram_storage — used by the mobile
     * variant pickers to read a model's configured options in one fetch. */
    @GetMapping("/models/{id}")
    public ResponseEntity<MasterModel> getModel(@PathVariable UUID id) {
        return modelRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/models")
    public ResponseEntity<MasterModel> createModel(@RequestBody ModelRequest req) {
        MasterModel e = MasterModel.builder()
                .brandId(req.getBrandId())
                .categoryId(req.getCategoryId())
                .seriesId(req.getSeriesId())
                .name(req.getName())
                .modelNumber(req.getModelNumber() != null ? req.getModelNumber() : new java.util.ArrayList<>())
                .slug(req.getSlug())
                .imageUrl(req.getImageUrl())
                .imageBase64(req.getImageBase64())
                .category(req.getCategory())
                .sellActive(req.getSellActive() == null ? Boolean.TRUE : req.getSellActive())
                .colors(req.getColors() != null ? req.getColors() : new java.util.ArrayList<>())
                .ramStorage(req.getRamStorage() != null ? req.getRamStorage() : new java.util.ArrayList<>())
                .build();
        return ResponseEntity.ok(modelRepo.save(e));
    }

    @PutMapping("/models/{id}")
    public ResponseEntity<MasterModel> updateModel(@PathVariable UUID id, @RequestBody ModelRequest req) {
        return modelRepo.findById(id)
                .map(e -> {
                    e.setBrandId(req.getBrandId());
                    if (req.getCategoryId() != null) e.setCategoryId(req.getCategoryId());
                    if (req.getSeriesId() != null) e.setSeriesId(req.getSeriesId());
                    e.setName(req.getName());
                    if (req.getModelNumber() != null) e.setModelNumber(req.getModelNumber());
                    if (req.getSlug() != null) e.setSlug(req.getSlug());
                    e.setImageUrl(req.getImageUrl());
                    if (req.getImageBase64() != null) e.setImageBase64(req.getImageBase64());
                    e.setCategory(req.getCategory());
                    if (req.getSellActive() != null) e.setSellActive(req.getSellActive());
                    if (req.getColors() != null) e.setColors(req.getColors());
                    if (req.getRamStorage() != null) e.setRamStorage(req.getRamStorage());
                    return ResponseEntity.ok(modelRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Toggle only the Sell-flow visibility flag — used by the admin Models table switch. */
    @PatchMapping("/models/{id}/sell-active")
    public ResponseEntity<MasterModel> setModelSellActive(@PathVariable UUID id, @RequestBody ModelRequest req) {
        return modelRepo.findById(id)
                .map(e -> {
                    e.setSellActive(req.getSellActive() != null ? req.getSellActive() : Boolean.TRUE);
                    return ResponseEntity.ok(modelRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/models/{id}")
    public ResponseEntity<Void> deleteModel(@PathVariable UUID id) {
        if (!modelRepo.existsById(id)) return ResponseEntity.notFound().build();
        modelRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- RAM options ----
    @GetMapping("/ram-options")
    public ResponseEntity<List<MasterRamOption>> getRamOptions() {
        return ResponseEntity.ok(ramRepo.findAll());
    }

    @PostMapping("/ram-options")
    public ResponseEntity<MasterRamOption> createRamOption(@RequestBody RamOptionRequest req) {
        MasterRamOption e = MasterRamOption.builder().valueGb(req.getValueGb()).label(req.getLabel()).build();
        return ResponseEntity.ok(ramRepo.save(e));
    }

    @PutMapping("/ram-options/{id}")
    public ResponseEntity<MasterRamOption> updateRamOption(@PathVariable UUID id, @RequestBody RamOptionRequest req) {
        return ramRepo.findById(id)
                .map(e -> {
                    e.setValueGb(req.getValueGb());
                    e.setLabel(req.getLabel());
                    return ResponseEntity.ok(ramRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/ram-options/{id}")
    public ResponseEntity<Void> deleteRamOption(@PathVariable UUID id) {
        if (!ramRepo.existsById(id)) return ResponseEntity.notFound().build();
        ramRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Storage options ----
    @GetMapping("/storage-options")
    public ResponseEntity<List<MasterStorageOption>> getStorageOptions() {
        return ResponseEntity.ok(storageRepo.findAll());
    }

    @PostMapping("/storage-options")
    public ResponseEntity<MasterStorageOption> createStorageOption(@RequestBody StorageOptionRequest req) {
        MasterStorageOption e = MasterStorageOption.builder().valueGb(req.getValueGb()).label(req.getLabel()).build();
        return ResponseEntity.ok(storageRepo.save(e));
    }

    @PutMapping("/storage-options/{id}")
    public ResponseEntity<MasterStorageOption> updateStorageOption(@PathVariable UUID id, @RequestBody StorageOptionRequest req) {
        return storageRepo.findById(id)
                .map(e -> {
                    e.setValueGb(req.getValueGb());
                    e.setLabel(req.getLabel());
                    return ResponseEntity.ok(storageRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/storage-options/{id}")
    public ResponseEntity<Void> deleteStorageOption(@PathVariable UUID id) {
        if (!storageRepo.existsById(id)) return ResponseEntity.notFound().build();
        storageRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Repair services (issues; scoped to device category + main category) ----
    @GetMapping("/repair-services")
    public ResponseEntity<List<MasterRepairService>> getRepairServices(
            @RequestParam(value = "deviceCategoryId", required = false) UUID deviceCategoryId,
            @RequestParam(value = "categoryId", required = false) UUID categoryId) {
        if (deviceCategoryId != null && categoryId != null) {
            return ResponseEntity.ok(repairServiceRepo.findByDeviceCategoryIdAndCategoryId(deviceCategoryId, categoryId));
        }
        if (deviceCategoryId != null) return ResponseEntity.ok(repairServiceRepo.findByDeviceCategoryId(deviceCategoryId));
        if (categoryId != null) return ResponseEntity.ok(repairServiceRepo.findByCategoryId(categoryId));
        return ResponseEntity.ok(repairServiceRepo.findAll());
    }

    /** Main categories with their nested issues for one device category (admin grouped view). */
    @GetMapping("/repair-services/grouped")
    public ResponseEntity<List<RepairCategoryGroup>> getRepairServicesGrouped(
            @RequestParam UUID deviceCategoryId) {
        var cats = repairCategoryRepo.findByDeviceCategoryIdOrderBySortOrderAscNameAsc(deviceCategoryId);
        var services = repairServiceRepo.findByDeviceCategoryId(deviceCategoryId);
        java.util.Map<UUID, java.util.List<MasterRepairService>> byCat = services.stream()
                .filter(s -> s.getCategoryId() != null)
                .collect(java.util.stream.Collectors.groupingBy(MasterRepairService::getCategoryId));
        List<RepairCategoryGroup> out = cats.stream()
                .map(c -> RepairCategoryGroup.builder()
                        .id(c.getId())
                        .code(c.getCode())
                        .name(c.getName())
                        .deviceCategoryId(c.getDeviceCategoryId())
                        .sortOrder(c.getSortOrder())
                        .isActive(c.getIsActive())
                        .issues(byCat.getOrDefault(c.getId(), java.util.List.of()))
                        .build())
                .toList();
        return ResponseEntity.ok(out);
    }

    /** Slug-up a display name into SCREAMING_SNAKE machine code. */
    private static String deriveCode(String name) {
        if (name == null) return null;
        String s = name.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return s.isBlank() ? null : s;
    }

    /** Unique repair-service code (auto from name, numeric suffix on collision). */
    private String uniqueServiceCode(String name, String provided) {
        if (provided != null && !provided.isBlank()) return provided;
        String base = deriveCode(name);
        if (base == null) base = "SERVICE";
        String code = base;
        int n = 2;
        while (repairServiceRepo.existsByCode(code)) { code = base + "_" + n++; }
        return code;
    }

    @PostMapping("/repair-services/bulk")
    public ResponseEntity<List<MasterRepairService>> createRepairServicesBulk(@RequestBody RepairServiceBulkRequest req) {
        java.util.List<MasterRepairService> out = new java.util.ArrayList<>();
        if (req.getNames() != null) {
            for (String name : req.getNames()) {
                if (name == null || name.isBlank()) continue;
                MasterRepairService e = MasterRepairService.builder()
                        .code(uniqueServiceCode(name, null))
                        .name(name.trim())
                        .categoryId(req.getCategoryId())
                        .deviceCategoryId(req.getDeviceCategoryId())
                        .build();
                out.add(repairServiceRepo.save(e));
            }
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping("/repair-services")
    public ResponseEntity<MasterRepairService> createRepairService(@RequestBody RepairServiceRequest req) {
        MasterRepairService e = MasterRepairService.builder()
                .code(uniqueServiceCode(req.getName(), req.getCode()))
                .name(req.getName())
                .description(req.getDescription())
                .categoryId(req.getCategoryId())
                .deviceCategoryId(req.getDeviceCategoryId())
                .iconUrl(req.getIconUrl())
                .iconBase64(req.getIconBase64())
                .build();
        return ResponseEntity.ok(repairServiceRepo.save(e));
    }

    @PutMapping("/repair-services/{id}")
    public ResponseEntity<MasterRepairService> updateRepairService(@PathVariable UUID id, @RequestBody RepairServiceRequest req) {
        return repairServiceRepo.findById(id)
                .map(e -> {
                    if (req.getCode() != null && !req.getCode().isBlank()) e.setCode(req.getCode());
                    e.setName(req.getName());
                    e.setDescription(req.getDescription());
                    if (req.getCategoryId() != null) e.setCategoryId(req.getCategoryId());
                    if (req.getDeviceCategoryId() != null) e.setDeviceCategoryId(req.getDeviceCategoryId());
                    if (req.getIconUrl() != null) e.setIconUrl(req.getIconUrl());
                    if (req.getIconBase64() != null) e.setIconBase64(req.getIconBase64());
                    return ResponseEntity.ok(repairServiceRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/repair-services/{id}")
    public ResponseEntity<Void> deleteRepairService(@PathVariable UUID id) {
        if (!repairServiceRepo.existsById(id)) return ResponseEntity.notFound().build();
        repairServiceRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
