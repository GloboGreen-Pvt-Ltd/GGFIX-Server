package com.repairshop.saas.ticket.controller;

import com.repairshop.saas.ticket.dto.LedgerEntryDtos.LedgerEntryRequest;
import com.repairshop.saas.ticket.dto.LedgerEntryDtos.LedgerEntryResponse;
import com.repairshop.saas.ticket.dto.LedgerEntryDtos.LedgerPeriodResponse;
import com.repairshop.saas.ticket.dto.LedgerEntryDtos.LedgerStatementResponse;
import com.repairshop.saas.ticket.service.ShopLedgerEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Money moving between the shop and one named customer or supplier — the
 * Received / Given entries behind every account.
 *
 * Gated exactly like the accounts themselves: scoped to the shopId on the
 * caller's JWT AND to the owner role. shopId alone is not enough, since a
 * technician's token also carries the shop, and who owes the shop what is not an
 * employee's to read or write. SHOP_LOGIN tokens carry the owner's role
 * (AuthService), so a counter login reaches this too.
 */
@RestController
@RequestMapping("/ledger-entries")
@RequiredArgsConstructor
@Tag(name = "Ledger Entries", description = "Received / Given on a customer or supplier account")
@SecurityRequirement(name = "Bearer")
public class ShopLedgerEntryController {

    private final ShopLedgerEntryService entryService;

    private UUID shopIdFrom(HttpServletRequest request) {
        String sid = (String) request.getAttribute("shopId");
        if (sid == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing shop context");
        return UUID.fromString(sid);
    }

    private UUID userIdFrom(HttpServletRequest request) {
        String uid = (String) request.getAttribute("userId");
        try {
            return uid != null ? UUID.fromString(uid) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void requireOwner(HttpServletRequest request) {
        Object r = request.getAttribute("roles");
        List<String> roles = (r instanceof List) ? (List<String>) r : List.of();
        if (!roles.contains("SHOP_OWNER") && !roles.contains("SUPER_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accounts are available to the shop owner.");
        }
    }

    @Operation(summary = "All movements in a date window, across every account")
    @GetMapping
    public LedgerPeriodResponse period(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        requireOwner(request);
        return entryService.period(shopIdFrom(request), from, to);
    }

    @Operation(summary = "One account's statement, with its balance and running totals")
    @GetMapping("/party/{partyId}")
    public LedgerStatementResponse statement(@PathVariable UUID partyId, HttpServletRequest request) {
        requireOwner(request);
        return entryService.statement(shopIdFrom(request), partyId);
    }

    @Operation(summary = "Record money received from, or given to, an account")
    @PostMapping("/party/{partyId}")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerEntryResponse create(@PathVariable UUID partyId,
                                      @RequestBody LedgerEntryRequest body,
                                      HttpServletRequest request) {
        requireOwner(request);
        return entryService.create(shopIdFrom(request), userIdFrom(request), partyId, body);
    }

    @Operation(summary = "Edit an entry (only the fields sent are changed)")
    @PatchMapping("/{id}")
    public LedgerEntryResponse update(@PathVariable UUID id,
                                      @RequestBody LedgerEntryRequest body,
                                      HttpServletRequest request) {
        requireOwner(request);
        return entryService.update(shopIdFrom(request), id, body);
    }

    @Operation(summary = "Delete an entry")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, HttpServletRequest request) {
        requireOwner(request);
        entryService.delete(shopIdFrom(request), id);
    }
}
