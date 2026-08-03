package com.repairshop.saas.marketplace.controller;

import com.repairshop.saas.marketplace.dto.ChatMessageResponse;
import com.repairshop.saas.marketplace.dto.ChatSendRequest;
import com.repairshop.saas.marketplace.dto.ChatThreadResponse;
import com.repairshop.saas.marketplace.entity.CustomerChatMessage;
import com.repairshop.saas.marketplace.service.ChatService;
import com.repairshop.saas.marketplace.service.ChatService.Side;
import com.repairshop.saas.marketplace.service.ChatSnapshotService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shop-side of the customer<->shop chat. Mirrors ChatController so the two
 * apps speak the same shape. Auth: requires the JWT to carry a shopId claim
 * (set by JwtAuthFilter on shop-owner / shop-mobile tokens).
 */
@RestController
@RequestMapping("/shop/chats")
@RequiredArgsConstructor
public class ShopChatController {

    private final ChatService chat;
    private final ChatSnapshotService snapshots;

    @GetMapping
    public ResponseEntity<List<ChatThreadResponse>> list(HttpServletRequest req) {
        UUID shopId = shopId(req);
        snapshots.touchShopPresence(shopId);
        List<ChatThreadResponse> out = chat.listForShop(shopId).stream()
            .map(t -> chat.toThreadResponse(t, Side.SHOP))
            .toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping("/{threadId}")
    public ResponseEntity<ChatThreadResponse> get(HttpServletRequest req, @PathVariable UUID threadId) {
        UUID shopId = shopId(req);
        snapshots.touchShopPresence(shopId);
        return ResponseEntity.ok(chat.toThreadResponse(
            chat.requireOwnedByShop(threadId, shopId), Side.SHOP));
    }

    @GetMapping("/{threadId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> messages(HttpServletRequest req, @PathVariable UUID threadId) {
        UUID shopId = shopId(req);
        snapshots.touchShopPresence(shopId);
        chat.requireOwnedByShop(threadId, shopId);
        List<ChatMessageResponse> out = chat.messagesFor(threadId).stream()
            .map(chat::toMessageResponse).toList();
        return ResponseEntity.ok(out);
    }

    @PostMapping("/{threadId}/messages")
    public ResponseEntity<ChatMessageResponse> send(HttpServletRequest req, @PathVariable UUID threadId, @RequestBody ChatSendRequest body) {
        UUID shopId = shopId(req);
        snapshots.touchShopPresence(shopId);
        chat.requireOwnedByShop(threadId, shopId);
        CustomerChatMessage m = chat.send(threadId, Side.SHOP, body);
        return ResponseEntity.ok(chat.toMessageResponse(m));
    }

    @PostMapping("/{threadId}/read")
    public ResponseEntity<Void> markRead(HttpServletRequest req, @PathVariable UUID threadId) {
        UUID shopId = shopId(req);
        snapshots.touchShopPresence(shopId);
        chat.requireOwnedByShop(threadId, shopId);
        chat.markRead(threadId, Side.SHOP);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{threadId}/typing")
    public ResponseEntity<Void> typing(HttpServletRequest req, @PathVariable UUID threadId,
                                       @RequestBody(required = false) Map<String, Object> body) {
        UUID shopId = shopId(req);
        snapshots.touchShopPresence(shopId);
        chat.requireOwnedByShop(threadId, shopId);
        boolean typing = body == null || body.get("typing") == null || Boolean.parseBoolean(String.valueOf(body.get("typing")));
        chat.setTyping(threadId, Side.SHOP, typing);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/presence")
    public ResponseEntity<Void> presence(HttpServletRequest req) {
        UUID shopId = shopId(req);
        snapshots.touchShopPresence(shopId);
        return ResponseEntity.noContent().build();
    }

    private UUID shopId(HttpServletRequest req) {
        Object s = req.getAttribute("shopId");
        if (s == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing shopId in token");
        return UUID.fromString(s.toString());
    }
}
