package com.repairshop.saas.marketplace.controller;

import com.repairshop.saas.marketplace.dto.CartAddRequest;
import com.repairshop.saas.marketplace.dto.CartItemResponse;
import com.repairshop.saas.marketplace.dto.CartUpdateRequest;
import com.repairshop.saas.marketplace.entity.CustomerCartItem;
import com.repairshop.saas.marketplace.exception.ResourceNotFoundException;
import com.repairshop.saas.marketplace.repository.CustomerCartItemRepository;
import com.repairshop.saas.marketplace.repository.MarketplaceProductRepository;
import com.repairshop.saas.marketplace.service.ProductMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/customer/cart")
@RequiredArgsConstructor
public class CartController {

    private final CustomerCartItemRepository cartRepo;
    private final MarketplaceProductRepository productRepo;

    @GetMapping
    public ResponseEntity<List<CartItemResponse>> list(HttpServletRequest req) {
        UUID userId = callerId(req);
        List<CartItemResponse> items = cartRepo.findByCustomerUserId(userId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(items);
    }

    @PostMapping
    public ResponseEntity<CartItemResponse> add(HttpServletRequest req, @RequestBody CartAddRequest body) {
        UUID userId = callerId(req);
        if (body.getProductId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId required");
        }
        int qty = body.getQuantity() == null || body.getQuantity() < 1 ? 1 : body.getQuantity();
        CustomerCartItem item = cartRepo.findByCustomerUserIdAndProductId(userId, body.getProductId())
                .orElseGet(() -> CustomerCartItem.builder()
                        .customerUserId(userId)
                        .productId(body.getProductId())
                        .quantity(0)
                        .build());
        item.setQuantity(item.getQuantity() == null ? qty : item.getQuantity() + qty);
        return ResponseEntity.ok(toResponse(cartRepo.save(item)));
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<CartItemResponse> update(HttpServletRequest req, @PathVariable UUID itemId, @RequestBody CartUpdateRequest body) {
        UUID userId = callerId(req);
        CustomerCartItem item = cartRepo.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));
        if (!item.getCustomerUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your cart item");
        }
        if (body.getQuantity() != null && body.getQuantity() > 0) {
            item.setQuantity(body.getQuantity());
        }
        return ResponseEntity.ok(toResponse(cartRepo.save(item)));
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> remove(HttpServletRequest req, @PathVariable UUID itemId) {
        UUID userId = callerId(req);
        CustomerCartItem item = cartRepo.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));
        if (!item.getCustomerUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your cart item");
        }
        cartRepo.delete(item);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(HttpServletRequest req) {
        UUID userId = callerId(req);
        cartRepo.deleteByCustomerUserId(userId);
        return ResponseEntity.noContent().build();
    }

    private CartItemResponse toResponse(CustomerCartItem c) {
        return CartItemResponse.builder()
                .id(c.getId())
                .productId(c.getProductId())
                .quantity(c.getQuantity())
                .product(productRepo.findById(c.getProductId()).map(ProductMapper::toResponse).orElse(null))
                .build();
    }

    private UUID callerId(HttpServletRequest req) {
        Object u = req.getAttribute("userId");
        if (u == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing userId");
        return UUID.fromString(u.toString());
    }
}
