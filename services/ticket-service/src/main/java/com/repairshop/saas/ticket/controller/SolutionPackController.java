package com.repairshop.saas.ticket.controller;

import com.repairshop.saas.ticket.dto.SolutionPackResponse;
import com.repairshop.saas.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Shop-wide solution pack search for the "Issue Reference Solution Pack View"
// screen. Ticket-scoped operations stay on TicketController.
@RestController
@RequestMapping("/solution-packs")
@RequiredArgsConstructor
@Tag(name = "Solution Packs", description = "Shop-wide knowledge base of solutions")
@SecurityRequirement(name = "Bearer")
public class SolutionPackController {

    private final TicketService ticketService;

    private UUID shopIdFrom(HttpServletRequest request) {
        String sid = (String) request.getAttribute("shopId");
        return sid != null ? UUID.fromString(sid) : null;
    }

    @GetMapping("/search")
    @Operation(summary = "Search solution packs across all tickets for the shop, filtered by brand/model/category")
    public List<SolutionPackResponse> search(
            @RequestParam(required = false) String packType,
            @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) UUID modelId,
            @RequestParam(required = false) UUID issueCategoryId,
            @RequestParam(required = false) UUID issueSubcategoryId,
            @RequestParam(required = false) String issueCategory,
            @RequestParam(required = false) String issueSubcategory,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.searchSolutionPacks(
                shopId, packType, brandId, modelId,
                issueCategoryId, issueSubcategoryId,
                issueCategory, issueSubcategory);
    }
}
