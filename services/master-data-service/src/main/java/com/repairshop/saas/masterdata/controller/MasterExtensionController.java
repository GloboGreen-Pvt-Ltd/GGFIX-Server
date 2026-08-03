package com.repairshop.saas.masterdata.controller;

import com.repairshop.saas.masterdata.dto.*;
import com.repairshop.saas.masterdata.entity.*;
import com.repairshop.saas.masterdata.repository.*;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Extended master-data CRUD endpoints (device categories, series, colors, model
 * variants, repair categories, sell-flow screening/conditions/issues, accessory
 * & warranty options, banners, FAQ items, app-content pages and support
 * contacts). Mirrors the response style of {@link MasterDataController}.
 */
@RestController
@RequestMapping("/master")
public class MasterExtensionController {

    private final MasterDeviceCategoryRepository deviceCategoryRepo;
    private final MasterDeviceSeriesRepository deviceSeriesRepo;
    private final MasterColorRepository colorRepo;
    private final MasterRepairCategoryRepository repairCategoryRepo;
    private final MasterScreeningQuestionRepository screeningQuestionRepo;
    private final MasterConditionGroupRepository conditionGroupRepo;
    private final MasterConditionOptionRepository conditionOptionRepo;
    private final MasterFunctionalIssueRepository functionalIssueRepo;
    private final MasterBannerRepository bannerRepo;
    private final MasterFaqItemRepository faqItemRepo;
    private final MasterAppContentRepository appContentRepo;
    private final MasterSupportContactRepository supportContactRepo;
    private final MasterCategoryBrandMappingRepository categoryBrandRepo;
    private final MasterModelRepository modelRepo;
    private final MasterBrandRepository brandRepo;

    public MasterExtensionController(MasterDeviceCategoryRepository deviceCategoryRepo,
                                     MasterDeviceSeriesRepository deviceSeriesRepo,
                                     MasterColorRepository colorRepo,
                                     MasterRepairCategoryRepository repairCategoryRepo,
                                     MasterScreeningQuestionRepository screeningQuestionRepo,
                                     MasterConditionGroupRepository conditionGroupRepo,
                                     MasterConditionOptionRepository conditionOptionRepo,
                                     MasterFunctionalIssueRepository functionalIssueRepo,
                                     MasterBannerRepository bannerRepo,
                                     MasterFaqItemRepository faqItemRepo,
                                     MasterAppContentRepository appContentRepo,
                                     MasterSupportContactRepository supportContactRepo,
                                     MasterCategoryBrandMappingRepository categoryBrandRepo,
                                     MasterModelRepository modelRepo,
                                     MasterBrandRepository brandRepo) {
        this.deviceCategoryRepo = deviceCategoryRepo;
        this.deviceSeriesRepo = deviceSeriesRepo;
        this.colorRepo = colorRepo;
        this.repairCategoryRepo = repairCategoryRepo;
        this.screeningQuestionRepo = screeningQuestionRepo;
        this.conditionGroupRepo = conditionGroupRepo;
        this.conditionOptionRepo = conditionOptionRepo;
        this.functionalIssueRepo = functionalIssueRepo;
        this.bannerRepo = bannerRepo;
        this.faqItemRepo = faqItemRepo;
        this.appContentRepo = appContentRepo;
        this.supportContactRepo = supportContactRepo;
        this.categoryBrandRepo = categoryBrandRepo;
        this.modelRepo = modelRepo;
        this.brandRepo = brandRepo;
    }

    private static int nz(Integer v) { return v == null ? 0 : v; }
    private static boolean nzb(Boolean v) { return v == null ? true : v; }

    /**
     * Slug-up: convert a display name into the SCREAMING_SNAKE machine code the
     * mobile app expects ("Audio Devices" -> "AUDIO_DEVICES"). Strips anything
     * that isn't A-Z/0-9 and trims leading/trailing underscores.
     */
    private static String deriveCode(String name) {
        if (name == null) return null;
        String s = name.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return s.isBlank() ? null : s;
    }

    // ---- Categories (alias for /device-categories, matches new "Categories" admin section) ----
    @GetMapping("/categories")
    public ResponseEntity<List<MasterDeviceCategory>> getCategoriesAlias() {
        return ResponseEntity.ok(deviceCategoryRepo.findAllByOrderByNameAsc());
    }

    @PostMapping("/categories")
    public ResponseEntity<MasterDeviceCategory> createCategoryAlias(@RequestBody DeviceCategoryRequest req) {
        return createDeviceCategory(req);
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<MasterDeviceCategory> updateCategoryAlias(@PathVariable UUID id, @RequestBody DeviceCategoryRequest req) {
        return updateDeviceCategory(id, req);
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategoryAlias(@PathVariable UUID id) {
        return deleteDeviceCategory(id);
    }

    /** Brands mapped to a single category (via category_brand_mapping). */
    @GetMapping("/categories/{categoryId}/brands")
    public ResponseEntity<List<MasterBrand>> getBrandsForCategory(@PathVariable UUID categoryId) {
        List<UUID> brandIds = categoryBrandRepo.findByCategoryId(categoryId).stream()
                .map(MasterCategoryBrandMapping::getBrandId)
                .toList();
        if (brandIds.isEmpty()) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(brandRepo.findAllById(brandIds));
    }

    /**
     * Lookup a category by its CODE (e.g. SMARTPHONE / LAPTOP). The mobile app
     * stores category codes in its Home tiles rather than UUIDs, so this lets
     * the app convert a code into the category row in one round trip.
     */
    @GetMapping("/categories/by-code/{code}")
    public ResponseEntity<MasterDeviceCategory> getCategoryByCode(@PathVariable String code) {
        return deviceCategoryRepo.findByCodeIgnoreCase(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Mobile-friendly: brands for a category identified by its CODE. */
    @GetMapping("/categories/by-code/{code}/brands")
    public ResponseEntity<List<MasterBrand>> getBrandsForCategoryCode(@PathVariable String code) {
        return deviceCategoryRepo.findByCodeIgnoreCase(code)
                .map(c -> getBrandsForCategory(c.getId()).getBody())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(List.of()));
    }

    /**
     * Convenience: series under (category, brand) without the caller having
     * to discover the mapping id first. Used by the mobile cascading picker.
     */
    @GetMapping("/categories/{categoryId}/brands/{brandId}/series")
    public ResponseEntity<List<MasterDeviceSeries>> getSeriesForCategoryAndBrand(@PathVariable UUID categoryId,
                                                                                  @PathVariable UUID brandId) {
        return categoryBrandRepo.findByCategoryIdAndBrandId(categoryId, brandId)
                .map(m -> ResponseEntity.ok(deviceSeriesRepo.findByCategoryBrandIdOrderBySortOrderAscNameAsc(m.getId())))
                .orElse(ResponseEntity.ok(List.of()));
    }

    /** Same as above but resolves the category by its CODE for mobile. */
    @GetMapping("/categories/by-code/{code}/brands/{brandId}/series")
    public ResponseEntity<List<MasterDeviceSeries>> getSeriesForCategoryCodeAndBrand(@PathVariable String code,
                                                                                      @PathVariable UUID brandId) {
        return deviceCategoryRepo.findByCodeIgnoreCase(code)
                .map(c -> getSeriesForCategoryAndBrand(c.getId(), brandId).getBody())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(List.of()));
    }

    // ---- Category-Brand mappings ----
    @GetMapping("/category-brand-mappings")
    public ResponseEntity<List<MasterCategoryBrandMapping>> getCategoryBrandMappings(
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "brandId", required = false) UUID brandId) {
        if (categoryId != null) return ResponseEntity.ok(categoryBrandRepo.findByCategoryId(categoryId));
        if (brandId != null) return ResponseEntity.ok(categoryBrandRepo.findByBrandId(brandId));
        return ResponseEntity.ok(categoryBrandRepo.findAll());
    }

    @PostMapping("/category-brand-mappings")
    public ResponseEntity<MasterCategoryBrandMapping> createCategoryBrandMapping(@RequestBody CategoryBrandMappingRequest req) {
        if (req.getCategoryId() == null || req.getBrandId() == null) {
            return ResponseEntity.badRequest().build();
        }
        return categoryBrandRepo.findByCategoryIdAndBrandId(req.getCategoryId(), req.getBrandId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(categoryBrandRepo.save(MasterCategoryBrandMapping.builder()
                        .categoryId(req.getCategoryId())
                        .brandId(req.getBrandId())
                        .build())));
    }

    @PutMapping("/category-brand-mappings/{id}")
    public ResponseEntity<MasterCategoryBrandMapping> updateCategoryBrandMapping(@PathVariable UUID id, @RequestBody CategoryBrandMappingRequest req) {
        return categoryBrandRepo.findById(id)
                .map(e -> {
                    if (req.getCategoryId() != null) e.setCategoryId(req.getCategoryId());
                    if (req.getBrandId() != null) e.setBrandId(req.getBrandId());
                    return ResponseEntity.ok(categoryBrandRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/category-brand-mappings/{id}")
    public ResponseEntity<Void> deleteCategoryBrandMapping(@PathVariable UUID id) {
        if (!categoryBrandRepo.existsById(id)) return ResponseEntity.notFound().build();
        categoryBrandRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** Series under a (category, brand) mapping. */
    @GetMapping("/category-brand-mappings/{mappingId}/series")
    public ResponseEntity<List<MasterDeviceSeries>> getSeriesForMapping(@PathVariable UUID mappingId) {
        return ResponseEntity.ok(deviceSeriesRepo.findByCategoryBrandIdOrderBySortOrderAscNameAsc(mappingId));
    }

    /** Models under a series (used by cascading admin pickers). */
    @GetMapping("/series/{seriesId}/models")
    public ResponseEntity<List<MasterModel>> getModelsForSeries(@PathVariable UUID seriesId) {
        return ResponseEntity.ok(modelRepo.findBySeriesIdOrderByName(seriesId));
    }

    // ---- Device categories (legacy paths kept for backward compat) ----
    @GetMapping("/device-categories")
    public ResponseEntity<List<MasterDeviceCategory>> getDeviceCategories() {
        return ResponseEntity.ok(deviceCategoryRepo.findAllByOrderByNameAsc());
    }

    @PostMapping("/device-categories")
    public ResponseEntity<MasterDeviceCategory> createDeviceCategory(@RequestBody DeviceCategoryRequest req) {
        // Admin form no longer collects `code` — derive it from the name so the
        // mobile app's saved-device filtering keeps working.
        String code = (req.getCode() == null || req.getCode().isBlank())
                ? deriveCode(req.getName())
                : req.getCode();
        MasterDeviceCategory e = MasterDeviceCategory.builder()
                .code(code)
                .name(req.getName())
                .imageUrl(req.getImageUrl())
                .imageBase64(req.getImageBase64())
                .isActive(nzb(req.getIsActive()))
                .build();
        return ResponseEntity.ok(deviceCategoryRepo.save(e));
    }

    @PutMapping("/device-categories/{id}")
    public ResponseEntity<MasterDeviceCategory> updateDeviceCategory(@PathVariable UUID id, @RequestBody DeviceCategoryRequest req) {
        return deviceCategoryRepo.findById(id)
                .map(e -> {
                    // Preserve existing code on edit unless the caller explicitly sets one.
                    if (req.getCode() != null && !req.getCode().isBlank()) {
                        e.setCode(req.getCode());
                    } else if (e.getCode() == null || e.getCode().isBlank()) {
                        e.setCode(deriveCode(req.getName()));
                    }
                    e.setName(req.getName());
                    e.setImageUrl(req.getImageUrl());
                    if (req.getImageBase64() != null) e.setImageBase64(req.getImageBase64());
                    if (req.getIsActive() != null) e.setIsActive(req.getIsActive());
                    return ResponseEntity.ok(deviceCategoryRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/device-categories/{id}")
    public ResponseEntity<Void> deleteDeviceCategory(@PathVariable UUID id) {
        if (!deviceCategoryRepo.existsById(id)) return ResponseEntity.notFound().build();
        deviceCategoryRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Device series ----
    @GetMapping("/brands/{brandId}/series")
    public ResponseEntity<List<MasterDeviceSeries>> getSeriesByBrand(@PathVariable UUID brandId) {
        return ResponseEntity.ok(deviceSeriesRepo.findByBrandIdOrderBySortOrderAscNameAsc(brandId));
    }

    @PostMapping("/series")
    public ResponseEntity<MasterDeviceSeries> createSeries(@RequestBody DeviceSeriesRequest req) {
        // If categoryBrandId is set, derive brandId from the mapping for backward compat.
        UUID derivedBrandId = req.getBrandId();
        if (req.getCategoryBrandId() != null) {
            derivedBrandId = categoryBrandRepo.findById(req.getCategoryBrandId())
                    .map(MasterCategoryBrandMapping::getBrandId)
                    .orElse(req.getBrandId());
        }
        MasterDeviceSeries e = MasterDeviceSeries.builder()
                .brandId(derivedBrandId)
                .categoryBrandId(req.getCategoryBrandId())
                .name(req.getName())
                .slug(req.getSlug())
                .sortOrder(nz(req.getSortOrder()))
                .build();
        return ResponseEntity.ok(deviceSeriesRepo.save(e));
    }

    @PutMapping("/series/{id}")
    public ResponseEntity<MasterDeviceSeries> updateSeries(@PathVariable UUID id, @RequestBody DeviceSeriesRequest req) {
        return deviceSeriesRepo.findById(id)
                .map(e -> {
                    if (req.getCategoryBrandId() != null) {
                        e.setCategoryBrandId(req.getCategoryBrandId());
                        categoryBrandRepo.findById(req.getCategoryBrandId())
                                .ifPresent(m -> e.setBrandId(m.getBrandId()));
                    } else if (req.getBrandId() != null) {
                        e.setBrandId(req.getBrandId());
                    }
                    e.setName(req.getName());
                    if (req.getSlug() != null) e.setSlug(req.getSlug());
                    if (req.getSortOrder() != null) e.setSortOrder(req.getSortOrder());
                    return ResponseEntity.ok(deviceSeriesRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/series/{id}")
    public ResponseEntity<Void> deleteSeries(@PathVariable UUID id) {
        if (!deviceSeriesRepo.existsById(id)) return ResponseEntity.notFound().build();
        deviceSeriesRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Colors ----
    @GetMapping("/colors")
    public ResponseEntity<List<MasterColor>> getColors() {
        return ResponseEntity.ok(colorRepo.findAllByOrderBySortOrderAscNameAsc());
    }

    @PostMapping("/colors")
    public ResponseEntity<MasterColor> createColor(@RequestBody ColorRequest req) {
        int nextSort = colorRepo.findTopByOrderBySortOrderDesc()
                .map(c -> nz(c.getSortOrder()) + 1)
                .orElse(0);
        MasterColor e = MasterColor.builder()
                .name(req.getName())
                .hexCode(req.getHexCode())
                .sortOrder(nextSort)
                .build();
        return ResponseEntity.ok(colorRepo.save(e));
    }

    @PutMapping("/colors/{id}")
    public ResponseEntity<MasterColor> updateColor(@PathVariable UUID id, @RequestBody ColorRequest req) {
        return colorRepo.findById(id)
                .map(e -> {
                    e.setName(req.getName());
                    e.setHexCode(req.getHexCode());
                    return ResponseEntity.ok(colorRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/colors/{id}")
    public ResponseEntity<Void> deleteColor(@PathVariable UUID id) {
        if (!colorRepo.existsById(id)) return ResponseEntity.notFound().build();
        colorRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** Unique, readable code from a name (optionally prefixed), with a numeric
     * suffix on collision so the unique constraint never trips the admin. */
    private String uniqueRepairCategoryCode(String prefix, String name, String provided) {
        if (provided != null && !provided.isBlank()) return provided;
        String base = deriveCode(name);
        if (base == null) base = "CATEGORY";
        if (prefix != null && !prefix.isBlank()) base = prefix + "_" + base;
        String code = base;
        int n = 2;
        while (repairCategoryRepo.existsByCode(code)) { code = base + "_" + n++; }
        return code;
    }

    /**
     * Build a globally-unique code: prefer the provided value, else derive it
     * from the name, then append a numeric suffix on collision. Lets the same
     * label exist under multiple device categories without tripping the unique
     * constraint on the code column.
     */
    private String uniqueCode(String provided, String name, java.util.function.Predicate<String> exists) {
        String base = (provided != null && !provided.isBlank()) ? provided : deriveCode(name);
        if (base == null || base.isBlank()) base = "ITEM";
        String code = base;
        int n = 2;
        while (exists.test(code)) { code = base + "_" + n++; }
        return code;
    }

    // ---- Repair categories (main categories; scoped to a device category) ----
    @GetMapping("/repair-categories")
    public ResponseEntity<List<MasterRepairCategory>> getRepairCategories(
            @RequestParam(value = "deviceCategoryId", required = false) UUID deviceCategoryId) {
        if (deviceCategoryId != null) {
            return ResponseEntity.ok(repairCategoryRepo.findByDeviceCategoryIdOrderBySortOrderAscNameAsc(deviceCategoryId));
        }
        return ResponseEntity.ok(repairCategoryRepo.findAllByOrderBySortOrderAscNameAsc());
    }

    @PostMapping("/repair-categories/bulk")
    public ResponseEntity<List<MasterRepairCategory>> createRepairCategoriesBulk(@RequestBody RepairCategoryBulkRequest req) {
        String devCode = req.getDeviceCategoryId() == null ? null
                : deviceCategoryRepo.findById(req.getDeviceCategoryId()).map(MasterDeviceCategory::getCode).orElse(null);
        java.util.List<MasterRepairCategory> out = new java.util.ArrayList<>();
        if (req.getNames() != null) {
            for (String name : req.getNames()) {
                if (name == null || name.isBlank()) continue;
                MasterRepairCategory e = MasterRepairCategory.builder()
                        .code(uniqueRepairCategoryCode(devCode, name, null))
                        .deviceCategoryId(req.getDeviceCategoryId())
                        .name(name.trim())
                        .sortOrder(0)
                        .isActive(true)
                        .build();
                out.add(repairCategoryRepo.save(e));
            }
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping("/repair-categories")
    public ResponseEntity<MasterRepairCategory> createRepairCategory(@RequestBody RepairCategoryRequest req) {
        String devCode = req.getDeviceCategoryId() == null ? null
                : deviceCategoryRepo.findById(req.getDeviceCategoryId()).map(MasterDeviceCategory::getCode).orElse(null);
        MasterRepairCategory e = MasterRepairCategory.builder()
                .code(uniqueRepairCategoryCode(devCode, req.getName(), req.getCode()))
                .deviceCategoryId(req.getDeviceCategoryId())
                .name(req.getName())
                .iconBase64(req.getIconBase64())
                .description(req.getDescription())
                .sortOrder(nz(req.getSortOrder()))
                .isActive(nzb(req.getIsActive()))
                .build();
        return ResponseEntity.ok(repairCategoryRepo.save(e));
    }

    @PutMapping("/repair-categories/{id}")
    public ResponseEntity<MasterRepairCategory> updateRepairCategory(@PathVariable UUID id, @RequestBody RepairCategoryRequest req) {
        return repairCategoryRepo.findById(id)
                .map(e -> {
                    if (req.getCode() != null && !req.getCode().isBlank()) e.setCode(req.getCode());
                    if (req.getDeviceCategoryId() != null) e.setDeviceCategoryId(req.getDeviceCategoryId());
                    e.setName(req.getName());
                    if (req.getIconBase64() != null) e.setIconBase64(req.getIconBase64());
                    e.setDescription(req.getDescription());
                    if (req.getSortOrder() != null) e.setSortOrder(req.getSortOrder());
                    if (req.getIsActive() != null) e.setIsActive(req.getIsActive());
                    return ResponseEntity.ok(repairCategoryRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/repair-categories/{id}")
    public ResponseEntity<Void> deleteRepairCategory(@PathVariable UUID id) {
        if (!repairCategoryRepo.existsById(id)) return ResponseEntity.notFound().build();
        repairCategoryRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Screening questions ----
    @GetMapping("/screening-questions")
    public ResponseEntity<List<MasterScreeningQuestion>> getScreeningQuestions(
            @RequestParam(required = false) String flow,
            @RequestParam(value = "deviceCategoryId", required = false) UUID deviceCategoryId) {
        List<MasterScreeningQuestion> list = screeningQuestionRepo.findAllByOrderBySortOrderAsc();
        if (flow != null && !flow.isBlank()) {
            // Return the requested flow plus shared COMMON questions, so a single
            // per-category question set surfaces for both WORKING and DEAD asks.
            final String f = flow.toUpperCase();
            list = list.stream()
                    .filter(x -> !Boolean.FALSE.equals(x.getIsActive()))
                    .filter(x -> {
                        String xf = x.getFlow() == null ? "" : x.getFlow().toUpperCase();
                        return xf.equals(f) || xf.equals("COMMON");
                    })
                    .toList();
        }
        if (deviceCategoryId != null) {
            list = list.stream()
                    .filter(x -> x.getDeviceCategoryId() == null || deviceCategoryId.equals(x.getDeviceCategoryId()))
                    .toList();
        }
        return ResponseEntity.ok(list);
    }

    @PostMapping("/screening-questions")
    public ResponseEntity<MasterScreeningQuestion> createScreeningQuestion(@RequestBody ScreeningQuestionRequest req) {
        MasterScreeningQuestion e = MasterScreeningQuestion.builder()
                .deviceCategoryId(req.getDeviceCategoryId())
                .flow(req.getFlow() == null || req.getFlow().isBlank() ? "COMMON" : req.getFlow())
                .question(req.getQuestion())
                .helperText(req.getHelperText())
                .sortOrder(nz(req.getSortOrder()))
                .isActive(nzb(req.getIsActive()))
                .build();
        return ResponseEntity.ok(screeningQuestionRepo.save(e));
    }

    @PutMapping("/screening-questions/{id}")
    public ResponseEntity<MasterScreeningQuestion> updateScreeningQuestion(@PathVariable UUID id, @RequestBody ScreeningQuestionRequest req) {
        return screeningQuestionRepo.findById(id)
                .map(e -> {
                    e.setDeviceCategoryId(req.getDeviceCategoryId());
                    if (req.getFlow() != null && !req.getFlow().isBlank()) e.setFlow(req.getFlow());
                    e.setQuestion(req.getQuestion());
                    e.setHelperText(req.getHelperText());
                    if (req.getSortOrder() != null) e.setSortOrder(req.getSortOrder());
                    if (req.getIsActive() != null) e.setIsActive(req.getIsActive());
                    return ResponseEntity.ok(screeningQuestionRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/screening-questions/{id}")
    public ResponseEntity<Void> deleteScreeningQuestion(@PathVariable UUID id) {
        if (!screeningQuestionRepo.existsById(id)) return ResponseEntity.notFound().build();
        screeningQuestionRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Condition groups ----
    @GetMapping("/condition-groups")
    public ResponseEntity<List<MasterConditionGroup>> getConditionGroups(
            @RequestParam(value = "deviceCategoryId", required = false) UUID deviceCategoryId) {
        List<MasterConditionGroup> list = conditionGroupRepo.findAllByOrderBySortOrderAscNameAsc();
        if (deviceCategoryId != null) {
            list = list.stream()
                    .filter(x -> x.getDeviceCategoryId() == null || deviceCategoryId.equals(x.getDeviceCategoryId()))
                    .toList();
        }
        return ResponseEntity.ok(list);
    }

    @PostMapping("/condition-groups")
    public ResponseEntity<MasterConditionGroup> createConditionGroup(@RequestBody ConditionGroupRequest req) {
        MasterConditionGroup e = MasterConditionGroup.builder()
                .code(uniqueCode(req.getCode(), req.getName(), conditionGroupRepo::existsByCode))
                .deviceCategoryId(req.getDeviceCategoryId())
                .name(req.getName())
                .flow(req.getFlow() == null ? "COMMON" : req.getFlow())
                .sortOrder(nz(req.getSortOrder()))
                .build();
        return ResponseEntity.ok(conditionGroupRepo.save(e));
    }

    @PutMapping("/condition-groups/{id}")
    public ResponseEntity<MasterConditionGroup> updateConditionGroup(@PathVariable UUID id, @RequestBody ConditionGroupRequest req) {
        return conditionGroupRepo.findById(id)
                .map(e -> {
                    if (req.getCode() != null && !req.getCode().isBlank()) e.setCode(req.getCode());
                    e.setDeviceCategoryId(req.getDeviceCategoryId());
                    e.setName(req.getName());
                    if (req.getFlow() != null) e.setFlow(req.getFlow());
                    if (req.getSortOrder() != null) e.setSortOrder(req.getSortOrder());
                    return ResponseEntity.ok(conditionGroupRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/condition-groups/{id}")
    public ResponseEntity<Void> deleteConditionGroup(@PathVariable UUID id) {
        if (!conditionGroupRepo.existsById(id)) return ResponseEntity.notFound().build();
        conditionGroupRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Condition options ----
    @GetMapping("/condition-groups/{groupId}/options")
    public ResponseEntity<List<MasterConditionOption>> getConditionOptions(@PathVariable UUID groupId) {
        return ResponseEntity.ok(conditionOptionRepo.findByGroupIdOrderBySortOrderAsc(groupId));
    }

    @PostMapping("/condition-options")
    public ResponseEntity<MasterConditionOption> createConditionOption(@RequestBody ConditionOptionRequest req) {
        MasterConditionOption e = MasterConditionOption.builder()
                .groupId(req.getGroupId())
                .label(req.getLabel())
                .iconUrl(req.getIconUrl())
                .iconBase64(req.getIconBase64())
                .priceImpact(req.getPriceImpact())
                .sortOrder(nz(req.getSortOrder()))
                .build();
        return ResponseEntity.ok(conditionOptionRepo.save(e));
    }

    @PutMapping("/condition-options/{id}")
    public ResponseEntity<MasterConditionOption> updateConditionOption(@PathVariable UUID id, @RequestBody ConditionOptionRequest req) {
        return conditionOptionRepo.findById(id)
                .map(e -> {
                    e.setGroupId(req.getGroupId());
                    e.setLabel(req.getLabel());
                    e.setIconUrl(req.getIconUrl());
                    if (req.getIconBase64() != null) e.setIconBase64(req.getIconBase64());
                    e.setPriceImpact(req.getPriceImpact());
                    if (req.getSortOrder() != null) e.setSortOrder(req.getSortOrder());
                    return ResponseEntity.ok(conditionOptionRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/condition-options/{id}")
    public ResponseEntity<Void> deleteConditionOption(@PathVariable UUID id) {
        if (!conditionOptionRepo.existsById(id)) return ResponseEntity.notFound().build();
        conditionOptionRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Functional issues ----
    @GetMapping("/functional-issues")
    public ResponseEntity<List<MasterFunctionalIssue>> getFunctionalIssues(
            @RequestParam(value = "deviceCategoryId", required = false) UUID deviceCategoryId) {
        List<MasterFunctionalIssue> list = functionalIssueRepo.findAllByOrderBySortOrderAscNameAsc();
        if (deviceCategoryId != null) {
            list = list.stream()
                    .filter(x -> x.getDeviceCategoryId() == null || deviceCategoryId.equals(x.getDeviceCategoryId()))
                    .toList();
        }
        return ResponseEntity.ok(list);
    }

    @PostMapping("/functional-issues")
    public ResponseEntity<MasterFunctionalIssue> createFunctionalIssue(@RequestBody FunctionalIssueRequest req) {
        MasterFunctionalIssue e = MasterFunctionalIssue.builder()
                .code(uniqueCode(req.getCode(), req.getName(), functionalIssueRepo::existsByCode))
                .deviceCategoryId(req.getDeviceCategoryId())
                .name(req.getName())
                .iconUrl(req.getIconUrl())
                .iconBase64(req.getIconBase64())
                .priceImpact(req.getPriceImpact())
                .sortOrder(nz(req.getSortOrder()))
                .isActive(nzb(req.getIsActive()))
                .build();
        return ResponseEntity.ok(functionalIssueRepo.save(e));
    }

    @PutMapping("/functional-issues/{id}")
    public ResponseEntity<MasterFunctionalIssue> updateFunctionalIssue(@PathVariable UUID id, @RequestBody FunctionalIssueRequest req) {
        return functionalIssueRepo.findById(id)
                .map(e -> {
                    if (req.getCode() != null && !req.getCode().isBlank()) e.setCode(req.getCode());
                    e.setDeviceCategoryId(req.getDeviceCategoryId());
                    e.setName(req.getName());
                    e.setIconUrl(req.getIconUrl());
                    if (req.getIconBase64() != null) e.setIconBase64(req.getIconBase64());
                    e.setPriceImpact(req.getPriceImpact());
                    if (req.getSortOrder() != null) e.setSortOrder(req.getSortOrder());
                    if (req.getIsActive() != null) e.setIsActive(req.getIsActive());
                    return ResponseEntity.ok(functionalIssueRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/functional-issues/{id}")
    public ResponseEntity<Void> deleteFunctionalIssue(@PathVariable UUID id) {
        if (!functionalIssueRepo.existsById(id)) return ResponseEntity.notFound().build();
        functionalIssueRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Banners ----
    @GetMapping("/banners")
    public ResponseEntity<List<MasterBanner>> getBanners() {
        return ResponseEntity.ok(bannerRepo.findAllByOrderBySortOrderAsc());
    }

    @PostMapping("/banners")
    public ResponseEntity<MasterBanner> createBanner(@RequestBody BannerRequest req) {
        MasterBanner e = MasterBanner.builder()
                .title(req.getTitle())
                .imageUrl(blankToNull(req.getImageUrl()))
                .imageBase64(req.getImageBase64())
                .linkTarget(blankToNull(req.getLinkTarget()))
                .sortOrder(nz(req.getSortOrder()))
                .isActive(nzb(req.getIsActive()))
                .build();
        return ResponseEntity.ok(bannerRepo.save(e));
    }

    @PutMapping("/banners/{id}")
    public ResponseEntity<MasterBanner> updateBanner(@PathVariable UUID id, @RequestBody BannerRequest req) {
        return bannerRepo.findById(id)
                .map(e -> {
                    // Every field is null-guarded, so a partial PUT only touches
                    // what it actually carries. title/imageUrl/linkTarget used to
                    // be assigned unconditionally, which meant a body like
                    // {"isActive": false} silently blanked the banner's title and
                    // image instead of just toggling it off.
                    if (req.getTitle() != null) e.setTitle(req.getTitle());
                    if (req.getImageUrl() != null) e.setImageUrl(blankToNull(req.getImageUrl()));
                    if (req.getImageBase64() != null) e.setImageBase64(req.getImageBase64());
                    if (req.getLinkTarget() != null) e.setLinkTarget(blankToNull(req.getLinkTarget()));
                    if (req.getSortOrder() != null) e.setSortOrder(req.getSortOrder());
                    if (req.getIsActive() != null) e.setIsActive(req.getIsActive());
                    return ResponseEntity.ok(bannerRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Clearing an optional field is expressed as "" by the admin form, which we
     * persist as NULL so "no image" is one value rather than two.
     */
    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    @DeleteMapping("/banners/{id}")
    public ResponseEntity<Void> deleteBanner(@PathVariable UUID id) {
        if (!bannerRepo.existsById(id)) return ResponseEntity.notFound().build();
        bannerRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- FAQ items ----
    @GetMapping("/faq-items")
    public ResponseEntity<List<MasterFaqItem>> getFaqItems() {
        return ResponseEntity.ok(faqItemRepo.findAllByOrderBySortOrderAsc());
    }

    @PostMapping("/faq-items")
    public ResponseEntity<MasterFaqItem> createFaqItem(@RequestBody FaqItemRequest req) {
        MasterFaqItem e = MasterFaqItem.builder()
                .question(req.getQuestion())
                .answer(req.getAnswer())
                .sortOrder(nz(req.getSortOrder()))
                .isActive(nzb(req.getIsActive()))
                .build();
        return ResponseEntity.ok(faqItemRepo.save(e));
    }

    @PutMapping("/faq-items/{id}")
    public ResponseEntity<MasterFaqItem> updateFaqItem(@PathVariable UUID id, @RequestBody FaqItemRequest req) {
        return faqItemRepo.findById(id)
                .map(e -> {
                    e.setQuestion(req.getQuestion());
                    e.setAnswer(req.getAnswer());
                    if (req.getSortOrder() != null) e.setSortOrder(req.getSortOrder());
                    if (req.getIsActive() != null) e.setIsActive(req.getIsActive());
                    return ResponseEntity.ok(faqItemRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/faq-items/{id}")
    public ResponseEntity<Void> deleteFaqItem(@PathVariable UUID id) {
        if (!faqItemRepo.existsById(id)) return ResponseEntity.notFound().build();
        faqItemRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---- App content (upsert by code) ----
    @GetMapping("/app-content")
    public ResponseEntity<List<MasterAppContent>> getAppContent() {
        return ResponseEntity.ok(appContentRepo.findAll());
    }

    @GetMapping("/app-content/{code}")
    public ResponseEntity<MasterAppContent> getAppContentByCode(@PathVariable String code) {
        return appContentRepo.findByCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/app-content")
    public ResponseEntity<MasterAppContent> upsertAppContent(@RequestBody AppContentRequest req) {
        return ResponseEntity.ok(upsertAppContentInternal(req));
    }

    @PutMapping("/app-content")
    public ResponseEntity<MasterAppContent> upsertAppContentPut(@RequestBody AppContentRequest req) {
        return ResponseEntity.ok(upsertAppContentInternal(req));
    }

    @DeleteMapping("/app-content/{id}")
    public ResponseEntity<Void> deleteAppContent(@PathVariable UUID id) {
        if (!appContentRepo.existsById(id)) return ResponseEntity.notFound().build();
        appContentRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private MasterAppContent upsertAppContentInternal(AppContentRequest req) {
        return appContentRepo.findByCode(req.getCode())
                .map(e -> {
                    e.setTitle(req.getTitle());
                    e.setBody(req.getBody());
                    return appContentRepo.save(e);
                })
                .orElseGet(() -> appContentRepo.save(MasterAppContent.builder()
                        .code(req.getCode())
                        .title(req.getTitle())
                        .body(req.getBody())
                        .build()));
    }

    // ---- Support contacts ----
    @GetMapping("/support-contacts")
    public ResponseEntity<List<MasterSupportContact>> getSupportContacts() {
        return ResponseEntity.ok(supportContactRepo.findAllByOrderBySortOrderAsc());
    }

    @PostMapping("/support-contacts")
    public ResponseEntity<MasterSupportContact> createSupportContact(@RequestBody SupportContactRequest req) {
        MasterSupportContact e = MasterSupportContact.builder()
                .label(req.getLabel())
                .email(req.getEmail())
                .phone(req.getPhone())
                .imageUrl(req.getImageUrl())
                .sortOrder(nz(req.getSortOrder()))
                .isActive(nzb(req.getIsActive()))
                .build();
        return ResponseEntity.ok(supportContactRepo.save(e));
    }

    @PutMapping("/support-contacts/{id}")
    public ResponseEntity<MasterSupportContact> updateSupportContact(@PathVariable UUID id, @RequestBody SupportContactRequest req) {
        return supportContactRepo.findById(id)
                .map(e -> {
                    e.setLabel(req.getLabel());
                    e.setEmail(req.getEmail());
                    e.setPhone(req.getPhone());
                    e.setImageUrl(req.getImageUrl());
                    if (req.getSortOrder() != null) e.setSortOrder(req.getSortOrder());
                    if (req.getIsActive() != null) e.setIsActive(req.getIsActive());
                    return ResponseEntity.ok(supportContactRepo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/support-contacts/{id}")
    public ResponseEntity<Void> deleteSupportContact(@PathVariable UUID id) {
        if (!supportContactRepo.existsById(id)) return ResponseEntity.notFound().build();
        supportContactRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
