package com.repairshop.saas.masterdata.controller;

import com.repairshop.saas.masterdata.entity.MasterTechnicianWorkStatus;
import com.repairshop.saas.masterdata.repository.MasterTechnicianWorkStatusRepository;

import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Admin-managed list of "Technician Work Status" dropdown options used by the
// employee Ticket Detail screen. Mirrors the response style of MasterDataController:
// flat /master/* GET/POST/PUT/DELETE, no shop scoping (catalog is platform-wide).
@RestController
@RequestMapping("/master/technician-work-statuses")
public class TechnicianWorkStatusController {

    private final MasterTechnicianWorkStatusRepository repo;

    public TechnicianWorkStatusController(MasterTechnicianWorkStatusRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public ResponseEntity<List<MasterTechnicianWorkStatus>> list(
            @RequestParam(value = "activeOnly", required = false) Boolean activeOnly) {
        // The employee app sends activeOnly=true so inactive rows don't clutter
        // the dropdown; the admin page omits it to see everything for editing.
        List<MasterTechnicianWorkStatus> rows = Boolean.TRUE.equals(activeOnly)
                ? repo.findByIsActiveTrueOrderBySortOrderAscLabelAsc()
                : repo.findAllByOrderBySortOrderAscLabelAsc();
        return ResponseEntity.ok(rows);
    }

    @PostMapping
    public ResponseEntity<MasterTechnicianWorkStatus> create(@RequestBody WorkStatusRequest req) {
        if (req.getLabel() == null || req.getLabel().isBlank()) return ResponseEntity.badRequest().build();
        String code = uniqueCode(req.getCode(), req.getLabel());
        Integer sort = req.getSortOrder() != null ? req.getSortOrder()
                : repo.findAllByOrderBySortOrderAscLabelAsc().size() * 10;
        // When the request omits ticketStatus, infer it from the label so the
        // admin form can stay minimal. Anything matching "done/complete/finish/
        // ready/delivered" maps to READY; everything else defaults to IN_REPAIR.
        String ticketStatus = req.getTicketStatus() != null && !req.getTicketStatus().isBlank()
                ? req.getTicketStatus().trim().toUpperCase()
                : inferTicketStatusFromLabel(req.getLabel());
        MasterTechnicianWorkStatus saved = repo.save(MasterTechnicianWorkStatus.builder()
                .code(code)
                .label(req.getLabel().trim())
                .ticketStatus(ticketStatus)
                .sortOrder(sort)
                .isActive(req.getIsActive() == null ? true : req.getIsActive())
                .build());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MasterTechnicianWorkStatus> update(
            @PathVariable UUID id, @RequestBody WorkStatusRequest req) {
        return repo.findById(id)
                .map(e -> {
                    if (req.getCode() != null && !req.getCode().isBlank()) e.setCode(req.getCode().trim());
                    if (req.getLabel() != null) {
                        e.setLabel(req.getLabel().trim());
                        // Label edits re-infer the ticket status when the
                        // request didn't pin one explicitly — keeps the
                        // single-field admin form coherent on rename.
                        if (req.getTicketStatus() == null || req.getTicketStatus().isBlank()) {
                            e.setTicketStatus(inferTicketStatusFromLabel(req.getLabel()));
                        }
                    }
                    if (req.getTicketStatus() != null && !req.getTicketStatus().isBlank()) {
                        e.setTicketStatus(req.getTicketStatus().trim().toUpperCase());
                    }
                    if (req.getSortOrder() != null) e.setSortOrder(req.getSortOrder());
                    if (req.getIsActive() != null) e.setIsActive(req.getIsActive());
                    return ResponseEntity.ok(repo.save(e));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private static String inferTicketStatusFromLabel(String label) {
        if (label == null) return "IN_REPAIR";
        String s = label.trim().toLowerCase();
        // "return for delivery" is a delivery-flow label so it should map to
        // READY alongside "ready" / "delivered" / "complete".
        if (s.contains("done") || s.contains("complete") || s.contains("finish")
                || s.contains("ready") || s.contains("delivered")
                || s.contains("return")) return "READY";
        if (s.contains("cancel")) return "CANCELLED";
        if (s.contains("quote")) return "QUOTED";
        if (s.contains("diagnos")) return "IN_DIAGNOSIS";
        return "IN_REPAIR";
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private String uniqueCode(String provided, String label) {
        if (provided != null && !provided.isBlank()) {
            String c = provided.trim().toUpperCase().replaceAll("[^A-Z0-9_]+", "_");
            if (!repo.existsByCode(c)) return c;
        }
        String base = label.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isBlank()) base = "WORK_STATUS";
        String code = base;
        int n = 2;
        while (repo.existsByCode(code)) { code = base + "_" + n++; }
        return code;
    }

    @Data
    public static class WorkStatusRequest {
        private String code;
        private String label;
        private String ticketStatus;
        private Integer sortOrder;
        private Boolean isActive;
    }
}
