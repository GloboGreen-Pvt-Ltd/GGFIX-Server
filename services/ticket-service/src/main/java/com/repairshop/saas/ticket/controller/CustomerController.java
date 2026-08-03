package com.repairshop.saas.ticket.controller;

import com.repairshop.saas.ticket.dto.CustomerLinkRequest;
import com.repairshop.saas.ticket.dto.CustomerRequest;
import com.repairshop.saas.ticket.dto.CustomerResponse;
import com.repairshop.saas.ticket.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer search and create for bookings")
@SecurityRequirement(name = "Bearer")
public class CustomerController {

    private final CustomerService customerService;

    private UUID shopIdFrom(HttpServletRequest request) {
        String sid = (String) request.getAttribute("shopId");
        return sid != null ? UUID.fromString(sid) : null;
    }

    private UUID requireShopId(HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing shop context");
        }
        return shopId;
    }

    @GetMapping
    @Operation(summary = "Search customers by name or phone")
    public List<CustomerResponse> list(
            @RequestParam(required = false, defaultValue = "") String q,
            HttpServletRequest request) {
        return customerService.search(requireShopId(request), q);
    }

    @GetMapping("/search")
    @Operation(summary = "Search customers by name or mobile for the New Booking flow")
    public List<CustomerResponse> search(
            @RequestParam(name = "query", required = false, defaultValue = "") String query,
            HttpServletRequest request) {
        String trimmed = query != null ? query.trim() : "";
        if (trimmed.length() < 2) return List.of();
        return customerService.search(requireShopId(request), trimmed);
    }

    @GetMapping("/lookup")
    @Operation(summary = "Lookup a customer by exact mobile (this shop first, then platform). 204 if not found.")
    public ResponseEntity<CustomerResponse> lookup(
            @RequestParam("mobile") String mobile,
            HttpServletRequest request) {
        return customerService.lookupByMobile(requireShopId(request), mobile)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new customer")
    public CustomerResponse create(@Valid @RequestBody CustomerRequest body, HttpServletRequest request) {
        return customerService.create(requireShopId(request), body);
    }

    @PostMapping("/link")
    @Operation(summary = "Link a platform customer_users row to this shop, creating a shop-scoped customers row if needed (idempotent)")
    public CustomerResponse link(@Valid @RequestBody CustomerLinkRequest body, HttpServletRequest request) {
        return customerService.linkPlatformUser(requireShopId(request), body.getPlatformUserId());
    }
}
