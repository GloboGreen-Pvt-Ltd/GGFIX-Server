package com.repairshop.saas.marketplace.controller;

import com.repairshop.saas.marketplace.dto.ChatMessageResponse;
import com.repairshop.saas.marketplace.dto.ChatSendRequest;
import com.repairshop.saas.marketplace.dto.ChatThreadResponse;
import com.repairshop.saas.marketplace.entity.CustomerChatMessage;
import com.repairshop.saas.marketplace.entity.CustomerChatThread;
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

@RestController
@RequestMapping("/customer/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chat;
    private final ChatSnapshotService snapshots;

    @GetMapping
    public ResponseEntity<List<ChatThreadResponse>> list(HttpServletRequest req) {
        UUID userId = callerId(req);
        snapshots.touchCustomerPresence(userId);
        List<ChatThreadResponse> out = chat.listForCustomer(userId).stream()
            .map(t -> chat.toThreadResponse(t, Side.CUSTOMER))
            .toList();
        return ResponseEntity.ok(out);
    }

    @PostMapping
    public ResponseEntity<ChatThreadResponse> open(HttpServletRequest req, @RequestParam("shopId") UUID shopId) {
        UUID userId = callerId(req);
        snapshots.touchCustomerPresence(userId);
        CustomerChatThread t = chat.openCustomerThread(userId, shopId);
        return ResponseEntity.ok(chat.toThreadResponse(t, Side.CUSTOMER));
    }

    @GetMapping("/{threadId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> messages(HttpServletRequest req, @PathVariable UUID threadId) {
        UUID userId = callerId(req);
        snapshots.touchCustomerPresence(userId);
        chat.requireOwnedByCustomer(threadId, userId);
        List<ChatMessageResponse> out = chat.messagesFor(threadId).stream()
            .map(chat::toMessageResponse).toList();
        return ResponseEntity.ok(out);
    }

    @PostMapping("/{threadId}/messages")
    public ResponseEntity<ChatMessageResponse> send(HttpServletRequest req, @PathVariable UUID threadId, @RequestBody ChatSendRequest body) {
        UUID userId = callerId(req);
        snapshots.touchCustomerPresence(userId);
        chat.requireOwnedByCustomer(threadId, userId);
        CustomerChatMessage m = chat.send(threadId, Side.CUSTOMER, body);
        return ResponseEntity.ok(chat.toMessageResponse(m));
    }

    @PostMapping("/{threadId}/read")
    public ResponseEntity<Void> markRead(HttpServletRequest req, @PathVariable UUID threadId) {
        UUID userId = callerId(req);
        snapshots.touchCustomerPresence(userId);
        chat.requireOwnedByCustomer(threadId, userId);
        chat.markRead(threadId, Side.CUSTOMER);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{threadId}/typing")
    public ResponseEntity<Void> typing(HttpServletRequest req, @PathVariable UUID threadId,
                                       @RequestBody(required = false) Map<String, Object> body) {
        UUID userId = callerId(req);
        snapshots.touchCustomerPresence(userId);
        chat.requireOwnedByCustomer(threadId, userId);
        boolean typing = body == null || body.get("typing") == null || Boolean.parseBoolean(String.valueOf(body.get("typing")));
        chat.setTyping(threadId, Side.CUSTOMER, typing);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/presence")
    public ResponseEntity<Void> presence(HttpServletRequest req) {
        UUID userId = callerId(req);
        snapshots.touchCustomerPresence(userId);
        return ResponseEntity.noContent().build();
    }

    private UUID callerId(HttpServletRequest req) {
        Object u = req.getAttribute("userId");
        if (u == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing userId");
        return UUID.fromString(u.toString());
    }
}
