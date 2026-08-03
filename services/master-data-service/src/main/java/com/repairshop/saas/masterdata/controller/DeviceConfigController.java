package com.repairshop.saas.masterdata.controller;

import com.repairshop.saas.masterdata.dto.ConfigFieldRequest;
import com.repairshop.saas.masterdata.dto.ConfigFieldResponse;
import com.repairshop.saas.masterdata.entity.MasterDeviceConfigField;
import com.repairshop.saas.masterdata.entity.MasterDeviceConfigOption;
import com.repairshop.saas.masterdata.repository.MasterDeviceConfigFieldRepository;
import com.repairshop.saas.masterdata.repository.MasterDeviceConfigOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Device configuration master data — per-category attribute "keys" (e.g. Laptop:
 * Device Processor, Available RAM) each with their selectable dropdown values.
 */
@RestController
@RequestMapping("/master")
@RequiredArgsConstructor
public class DeviceConfigController {

    private final MasterDeviceConfigFieldRepository fieldRepo;
    private final MasterDeviceConfigOptionRepository optionRepo;

    private static String deriveCode(String name) {
        if (name == null) return null;
        String s = name.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return s.isBlank() ? null : s;
    }
    private static int nz(Integer v) { return v == null ? 0 : v; }
    private static boolean nzb(Boolean v) { return v == null || v; }

    @GetMapping("/config-fields")
    public ResponseEntity<List<ConfigFieldResponse>> list(
            @RequestParam(value = "deviceCategoryId", required = false) UUID deviceCategoryId) {
        List<MasterDeviceConfigField> fields = deviceCategoryId != null
                ? fieldRepo.findByDeviceCategoryIdOrderBySortOrderAscNameAsc(deviceCategoryId)
                : fieldRepo.findAllByOrderBySortOrderAscNameAsc();
        return ResponseEntity.ok(fields.stream().map(this::toResponse).toList());
    }

    @PostMapping("/config-fields")
    @Transactional
    public ResponseEntity<ConfigFieldResponse> create(@RequestBody ConfigFieldRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        MasterDeviceConfigField f = fieldRepo.save(MasterDeviceConfigField.builder()
                .deviceCategoryId(req.getDeviceCategoryId())
                .code(deriveCode(req.getName()))
                .name(req.getName().trim())
                .sortOrder(nz(req.getSortOrder()))
                .isActive(nzb(req.getIsActive()))
                .build());
        saveOptions(f.getId(), req.getOptions());
        return ResponseEntity.ok(toResponse(f));
    }

    @PutMapping("/config-fields/{id}")
    @Transactional
    public ResponseEntity<ConfigFieldResponse> update(@PathVariable UUID id, @RequestBody ConfigFieldRequest req) {
        return fieldRepo.findById(id)
                .map(f -> {
                    if (req.getDeviceCategoryId() != null) f.setDeviceCategoryId(req.getDeviceCategoryId());
                    if (req.getName() != null && !req.getName().isBlank()) {
                        f.setName(req.getName().trim());
                        f.setCode(deriveCode(req.getName()));
                    }
                    if (req.getSortOrder() != null) f.setSortOrder(req.getSortOrder());
                    if (req.getIsActive() != null) f.setIsActive(req.getIsActive());
                    fieldRepo.save(f);
                    if (req.getOptions() != null) {
                        optionRepo.deleteByFieldId(f.getId());
                        saveOptions(f.getId(), req.getOptions());
                    }
                    return ResponseEntity.ok(toResponse(f));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/config-fields/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!fieldRepo.existsById(id)) return ResponseEntity.notFound().build();
        optionRepo.deleteByFieldId(id);
        fieldRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void saveOptions(UUID fieldId, List<String> values) {
        if (values == null) return;
        int sort = 0;
        for (String v : values) {
            if (v == null || v.isBlank()) continue;
            optionRepo.save(MasterDeviceConfigOption.builder()
                    .fieldId(fieldId).value(v.trim()).sortOrder(sort++).build());
        }
    }

    private ConfigFieldResponse toResponse(MasterDeviceConfigField f) {
        List<ConfigFieldResponse.OptionDto> opts = optionRepo.findByFieldIdOrderBySortOrderAsc(f.getId())
                .stream()
                .map(o -> ConfigFieldResponse.OptionDto.builder()
                        .id(o.getId()).value(o.getValue()).sortOrder(o.getSortOrder()).build())
                .toList();
        return ConfigFieldResponse.builder()
                .id(f.getId())
                .deviceCategoryId(f.getDeviceCategoryId())
                .code(f.getCode())
                .name(f.getName())
                .sortOrder(f.getSortOrder())
                .isActive(f.getIsActive())
                .options(opts)
                .build();
    }
}
