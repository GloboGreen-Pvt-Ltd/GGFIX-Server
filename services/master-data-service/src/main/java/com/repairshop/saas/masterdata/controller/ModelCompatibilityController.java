package com.repairshop.saas.masterdata.controller;

import com.repairshop.saas.masterdata.dto.ModelCompatibilityRequest;
import com.repairshop.saas.masterdata.entity.CompatibleModelRef;
import com.repairshop.saas.masterdata.entity.MasterBrand;
import com.repairshop.saas.masterdata.entity.MasterModel;
import com.repairshop.saas.masterdata.entity.ModelCompatibility;
import com.repairshop.saas.masterdata.entity.ModelCompatibilityType;
import com.repairshop.saas.masterdata.repository.MasterBrandRepository;
import com.repairshop.saas.masterdata.repository.MasterModelRepository;
import com.repairshop.saas.masterdata.repository.ModelCompatibilityRepository;
import com.repairshop.saas.masterdata.repository.ModelCompatibilityTypeRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spare-part boxes and the models they fit — Admin panel -> Master Data ->
 * Model Compatibility.
 *
 * Its own controller rather than more methods on {@link MasterExtensionController},
 * which is already 745 lines covering a dozen unrelated tables. Same
 * {@code /master} prefix, so it is reachable at the same edge location and needs
 * no nginx change.
 *
 * The reference-image upload lives on {@link ModelMediaController} with the other
 * S3-backed uploads, because it needs the multipart plumbing those share.
 */
@RestController
@RequestMapping("/master")
public class ModelCompatibilityController {

    private final ModelCompatibilityRepository repo;
    private final ModelCompatibilityTypeRepository typeRepo;
    private final MasterModelRepository modelRepo;
    private final MasterBrandRepository brandRepo;

    public ModelCompatibilityController(ModelCompatibilityRepository repo,
                                        ModelCompatibilityTypeRepository typeRepo,
                                        MasterModelRepository modelRepo,
                                        MasterBrandRepository brandRepo) {
        this.repo = repo;
        this.typeRepo = typeRepo;
        this.modelRepo = modelRepo;
        this.brandRepo = brandRepo;
    }

    /* ------------------------------------------------------- part types -- */

    /**
     * The part types the admin sidebar builds its child entries from. Ordered by
     * sort_order then name, so the menu order is data, not a code constant.
     */
    @GetMapping("/model-compatibility-types")
    public ResponseEntity<List<ModelCompatibilityType>> listTypes() {
        return ResponseEntity.ok(typeRepo.findAllByOrderBySortOrderAscNameAsc());
    }

    @PostMapping("/model-compatibility-types")
    public ResponseEntity<?> createType(@RequestBody ModelCompatibilityType req) {
        String name = trimToNull(req.getName());
        if (name == null) return badRequest("Type name is required.");

        Optional<ModelCompatibilityType> clash = typeRepo.findByNameIgnoreCase(name);
        if (clash.isPresent()) return conflict("A part type named \"" + name + "\" already exists.");

        String slug = uniqueSlug(trimToNull(req.getSlug()) != null ? req.getSlug() : name, null);
        ModelCompatibilityType e = ModelCompatibilityType.builder()
                .name(name)
                .slug(slug)
                .sortOrder(req.getSortOrder() == null ? nextSortOrder() : req.getSortOrder())
                .isActive(req.getIsActive() == null || req.getIsActive())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(typeRepo.save(e));
    }

    @PutMapping("/model-compatibility-types/{id}")
    public ResponseEntity<?> updateType(@PathVariable UUID id, @RequestBody ModelCompatibilityType req) {
        Optional<ModelCompatibilityType> found = typeRepo.findById(id);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        ModelCompatibilityType e = found.get();

        if (req.getName() != null) {
            String name = trimToNull(req.getName());
            if (name == null) return badRequest("Type name cannot be blank.");
            Optional<ModelCompatibilityType> clash = typeRepo.findByNameIgnoreCase(name);
            if (clash.isPresent() && !clash.get().getId().equals(id)) {
                return conflict("A part type named \"" + name + "\" already exists.");
            }
            // The slug follows a rename, so the sidebar link keeps matching the
            // label. Bookmarks to the old slug break — acceptable for an internal
            // admin, and the alternative is a menu whose URL contradicts its text.
            e.setName(name);
            e.setSlug(uniqueSlug(name, id));
        }
        if (req.getSortOrder() != null) e.setSortOrder(req.getSortOrder());
        if (req.getIsActive() != null) e.setIsActive(req.getIsActive());
        return ResponseEntity.ok(typeRepo.save(e));
    }

    /**
     * Refuses while boxes still point at the type. Deleting anyway would leave
     * those boxes reachable only from "All", which reads as data loss.
     */
    @DeleteMapping("/model-compatibility-types/{id}")
    public ResponseEntity<?> deleteType(@PathVariable UUID id) {
        if (!typeRepo.existsById(id)) return ResponseEntity.notFound().build();
        long inUse = repo.countByPartTypeId(id);
        if (inUse > 0) {
            return conflict("That type still holds " + inUse + " box" + (inUse == 1 ? "" : "es")
                    + ". Move them to another type first.");
        }
        typeRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private int nextSortOrder() {
        return typeRepo.findAll().stream()
                .map(ModelCompatibilityType::getSortOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 10;
    }

    /**
     * Slugify, then de-duplicate with a numeric suffix. Two types named closely
     * enough to collide ("Mobile Case" / "Mobile-Case") would otherwise trip the
     * unique index with a 500 instead of just being filed as mobile-case-2.
     */
    private String uniqueSlug(String source, UUID selfId) {
        String base = source == null ? "" : source.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.isBlank()) base = "type";
        String candidate = base;
        for (int n = 2; n < 1000; n++) {
            Optional<ModelCompatibilityType> hit = typeRepo.findBySlugIgnoreCase(candidate);
            if (hit.isEmpty() || (selfId != null && hit.get().getId().equals(selfId))) return candidate;
            candidate = base + "-" + n;
        }
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /* ------------------------------------------------------------ boxes -- */

    /**
     * All boxes, newest ordering rules first: sort_order then box number.
     *
     * @param modelId  keep only boxes that list this model — "which box holds the
     *                 part for this device?", the question the shop actually asks
     * @param brandId  keep only boxes that list at least one model of this brand
     * @param activeOnly drop boxes switched off in the admin
     *
     * Both filters run in memory. The whole table is one row per box on a shelf —
     * hundreds, not millions — so a jsonb containment query would buy nothing and
     * would tie this endpoint to Postgres, which the H2 dev profile is not.
     */
    @GetMapping("/model-compatibility")
    public ResponseEntity<List<ModelCompatibility>> list(
            @RequestParam(value = "modelId", required = false) UUID modelId,
            @RequestParam(value = "brandId", required = false) UUID brandId,
            @RequestParam(value = "typeId", required = false) UUID typeId,
            @RequestParam(value = "type", required = false) String typeSlug,
            @RequestParam(value = "activeOnly", required = false) Boolean activeOnly) {

        List<ModelCompatibility> rows = repo.findAllByOrderBySortOrderAscBoxNoAsc();

        // The admin sidebar links by slug (?type=tempered-glass) so the URL reads;
        // an unknown slug filters to nothing rather than silently listing every
        // box, which would look like the menu entry did nothing.
        UUID wantedType = typeId;
        if (wantedType == null && typeSlug != null && !typeSlug.isBlank()) {
            Optional<ModelCompatibilityType> t = typeRepo.findBySlugIgnoreCase(typeSlug.trim());
            if (t.isEmpty()) return ResponseEntity.ok(List.of());
            wantedType = t.get().getId();
        }
        if (wantedType != null) {
            final UUID want = wantedType;
            rows = rows.stream().filter(r -> want.equals(r.getPartTypeId())).toList();
        }

        if (Boolean.TRUE.equals(activeOnly)) {
            rows = rows.stream().filter(r -> !Boolean.FALSE.equals(r.getIsActive())).toList();
        }
        if (modelId != null) {
            rows = rows.stream().filter(r -> refs(r).stream()
                    .anyMatch(m -> modelId.equals(m.getModelId()))).toList();
        }
        if (brandId != null) {
            rows = rows.stream().filter(r -> refs(r).stream()
                    .anyMatch(m -> brandId.equals(m.getBrandId()))).toList();
        }
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/model-compatibility/{id}")
    public ResponseEntity<ModelCompatibility> get(@PathVariable UUID id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/model-compatibility")
    public ResponseEntity<?> create(@RequestBody ModelCompatibilityRequest req) {
        String boxNo = trimToNull(req.getBoxNo());
        String boxName = trimToNull(req.getBoxName());
        if (boxNo == null) return badRequest("Box No is required.");
        if (boxName == null) return badRequest("Box Name is required.");

        Optional<ModelCompatibility> clash = repo.findByBoxNoIgnoreCase(boxNo);
        if (clash.isPresent()) {
            return conflict("Box No \"" + boxNo + "\" is already used by \""
                    + clash.get().getBoxName() + "\".");
        }

        List<CompatibleModelRef> models;
        try {
            models = resolveModels(req.getModels());
        } catch (UnknownModelException e) {
            return badRequest(e.getMessage());
        }

        if (req.getPartTypeId() != null && !typeRepo.existsById(req.getPartTypeId())) {
            return badRequest("No part type exists for id " + req.getPartTypeId() + ".");
        }

        ModelCompatibility e = ModelCompatibility.builder()
                .partTypeId(req.getPartTypeId())
                .boxNo(boxNo)
                .boxName(boxName)
                .models(models)
                .referenceImageUrl(trimToNull(req.getReferenceImageUrl()))
                .notes(trimToNull(req.getNotes()))
                .sortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder())
                .isActive(req.getIsActive() == null || req.getIsActive())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(e));
    }

    /**
     * Partial update: a field left out of the body is left alone. That is what
     * lets the admin's Active toggle send {"isActive": false} without blanking
     * the box name — the same bug that was fixed on banners.
     */
    @PutMapping("/model-compatibility/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody ModelCompatibilityRequest req) {
        Optional<ModelCompatibility> found = repo.findById(id);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        ModelCompatibility e = found.get();

        if (req.getBoxNo() != null) {
            String boxNo = trimToNull(req.getBoxNo());
            if (boxNo == null) return badRequest("Box No cannot be blank.");
            Optional<ModelCompatibility> clash = repo.findByBoxNoIgnoreCase(boxNo);
            if (clash.isPresent() && !clash.get().getId().equals(id)) {
                return conflict("Box No \"" + boxNo + "\" is already used by \""
                        + clash.get().getBoxName() + "\".");
            }
            e.setBoxNo(boxNo);
        }
        if (req.getBoxName() != null) {
            String boxName = trimToNull(req.getBoxName());
            if (boxName == null) return badRequest("Box Name cannot be blank.");
            e.setBoxName(boxName);
        }
        if (req.getModels() != null) {
            try {
                e.setModels(resolveModels(req.getModels()));
            } catch (UnknownModelException ex) {
                return badRequest(ex.getMessage());
            }
        }
        if (req.getPartTypeId() != null) {
            if (!typeRepo.existsById(req.getPartTypeId())) {
                return badRequest("No part type exists for id " + req.getPartTypeId() + ".");
            }
            e.setPartTypeId(req.getPartTypeId());
        }
        if (req.getReferenceImageUrl() != null) e.setReferenceImageUrl(trimToNull(req.getReferenceImageUrl()));
        if (req.getNotes() != null) e.setNotes(trimToNull(req.getNotes()));
        if (req.getSortOrder() != null) e.setSortOrder(req.getSortOrder());
        if (req.getIsActive() != null) e.setIsActive(req.getIsActive());

        return ResponseEntity.ok(repo.save(e));
    }

    @DeleteMapping("/model-compatibility/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /* ------------------------------------------------------------------ */

    /**
     * Turn the ids the client ticked into stored refs carrying the brand and
     * model names.
     *
     * Names are read from master_models / master_brands here rather than trusted
     * from the request, so a box can never be labelled with a model name that
     * does not belong to the id it stores. Duplicate ids collapse; the client's
     * ordering is kept so the admin's checkbox order survives a round trip.
     *
     * @throws UnknownModelException if an id has no model — silently dropping it
     *                               would show the admin a saved box missing a
     *                               model they had just ticked
     */
    private List<CompatibleModelRef> resolveModels(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();

        List<UUID> wanted = new ArrayList<>(new LinkedHashSet<>(ids.stream().filter(java.util.Objects::nonNull).toList()));
        Map<UUID, MasterModel> byId = modelRepo.findAllById(wanted).stream()
                .collect(Collectors.toMap(MasterModel::getId, Function.identity()));

        List<UUID> missing = wanted.stream().filter(id -> !byId.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new UnknownModelException("No model exists for id " + missing.get(0)
                    + ". Reload the page and pick the models again.");
        }

        List<UUID> brandIds = byId.values().stream()
                .map(MasterModel::getBrandId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, String> brandNames = brandRepo.findAllById(brandIds).stream()
                .collect(Collectors.toMap(MasterBrand::getId, MasterBrand::getName));

        List<CompatibleModelRef> out = new ArrayList<>(wanted.size());
        for (UUID id : wanted) {
            MasterModel m = byId.get(id);
            out.add(CompatibleModelRef.builder()
                    .brandId(m.getBrandId())
                    .brandName(m.getBrandId() == null ? null : brandNames.get(m.getBrandId()))
                    .modelId(m.getId())
                    .modelName(m.getName())
                    .build());
        }
        return out;
    }

    /** Null-safe view of the stored array — a legacy row could hold SQL NULL. */
    private static List<CompatibleModelRef> refs(ModelCompatibility row) {
        return row.getModels() == null ? List.of() : row.getModels();
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    /** Same body shape as MediaExceptionHandler, so the admin renders it verbatim. */
    private static ResponseEntity<Map<String, Object>> problem(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String message) {
        return problem(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseEntity<Map<String, Object>> conflict(String message) {
        return problem(HttpStatus.CONFLICT, message);
    }

    /** Local to this controller — a bad id is a request problem, not a media one. */
    private static final class UnknownModelException extends RuntimeException {
        UnknownModelException(String message) {
            super(message);
        }
    }
}
