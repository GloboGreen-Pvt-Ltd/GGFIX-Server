package com.repairshop.saas.ticket.controller;

import com.repairshop.saas.ticket.dto.LedgerPartyDtos.LedgerPartyRequest;
import com.repairshop.saas.ticket.dto.LedgerPartyDtos.LedgerPartyResponse;
import com.repairshop.saas.ticket.service.ShopLedgerPartyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * The shop's customer / supplier accounts — the two books behind the Cash Book
 * screen's Customer and Supplier tabs.
 *
 * Gated exactly like the cash book: scoped to the shopId on the caller's JWT
 * AND to the owner role. shopId alone is not enough, since a technician's token
 * also carries the shop, and who the shop owes money to is not an employee's to
 * read. SHOP_LOGIN tokens carry the owner's role (AuthService), so a counter
 * login reaches this too.
 */
@RestController
@RequestMapping("/ledger-parties")
@RequiredArgsConstructor
@Tag(name = "Ledger Parties", description = "Shop customer / supplier accounts")
@SecurityRequirement(name = "Bearer")
public class ShopLedgerPartyController {

    private final ShopLedgerPartyService partyService;

    private UUID shopIdFrom(HttpServletRequest request) {
        String sid = (String) request.getAttribute("shopId");
        if (sid == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing shop context");
        return UUID.fromString(sid);
    }

    @SuppressWarnings("unchecked")
    private void requireOwner(HttpServletRequest request) {
        Object r = request.getAttribute("roles");
        List<String> roles = (r instanceof List) ? (List<String>) r : List.of();
        if (!roles.contains("SHOP_OWNER") && !roles.contains("SUPER_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accounts are available to the shop owner.");
        }
    }

    @Operation(summary = "List the shop's customer or supplier accounts")
    @GetMapping
    public List<LedgerPartyResponse> list(@RequestParam(defaultValue = "CUSTOMER") String partyType,
                                          HttpServletRequest request) {
        requireOwner(request);
        return partyService.list(shopIdFrom(request), partyType);
    }

    @Operation(summary = "One account with its current balance")
    @GetMapping("/{id}")
    public LedgerPartyResponse get(@PathVariable UUID id, HttpServletRequest request) {
        requireOwner(request);
        return partyService.get(shopIdFrom(request), id);
    }

    @Operation(summary = "Add an account, or update the name on the one holding this number")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerPartyResponse create(@RequestBody LedgerPartyRequest body, HttpServletRequest request) {
        requireOwner(request);
        return partyService.create(shopIdFrom(request), body);
    }

    @Operation(summary = "Edit an account (only the fields sent are changed)")
    @PatchMapping("/{id}")
    public LedgerPartyResponse update(@PathVariable UUID id,
                                      @RequestBody LedgerPartyRequest body,
                                      HttpServletRequest request) {
        requireOwner(request);
        return partyService.update(shopIdFrom(request), id, body);
    }

    @Operation(summary = "Delete an account")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, HttpServletRequest request) {
        requireOwner(request);
        partyService.delete(shopIdFrom(request), id);
    }
}
