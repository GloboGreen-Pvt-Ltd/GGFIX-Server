package com.repairshop.saas.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repairshop.saas.order.dto.SellOrderDtos.*;
import com.repairshop.saas.order.entity.*;
import com.repairshop.saas.order.exception.ForbiddenException;
import com.repairshop.saas.order.exception.ResourceNotFoundException;
import com.repairshop.saas.order.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/sell-orders")
@RequiredArgsConstructor
public class SellOrderController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final SellOrderRepository sellRepo;
    private final SellOrderScreeningAnswerRepository answerRepo;
    private final SellOrderConditionRepository conditionRepo;
    private final SellOrderIssueRepository issueRepo;
    private final SellOrderAccessoryRepository accRepo;
    private final SellOrderQuotationRepository quoteRepo;
    private final CustomerOrderRepository customerOrderRepo;
    private final ObjectMapper objectMapper;

    @PostMapping
    @Transactional
    public ResponseEntity<SellOrderResponse> create(HttpServletRequest req, @RequestBody SellOrderRequest body) {
        UUID userId = callerId(req);
        String sellNumber = uniqueSellNumber();
        ImageBundle imgs = body.getImages() != null ? body.getImages() : ImageBundle.builder().build();
        SellOrder saved = sellRepo.save(SellOrder.builder()
                .sellNumber(sellNumber)
                .customerUserId(userId)
                .addressId(body.getAddressId())
                .brandId(body.getBrandId())
                .modelId(body.getModelId())
                .ramOptionId(body.getRamOptionId())
                .storageOptionId(body.getStorageOptionId())
                .color(body.getColor())
                .imei(body.getImei())
                .workingCondition(body.getWorkingCondition())
                .warrantyCode(body.getWarrantyCode())
                .frontImageUrl(imgs.getFront())
                .backImageUrl(imgs.getBack())
                .sideImageUrl(imgs.getSide())
                .cameraImageUrl(imgs.getCamera())
                .otherImageUrl(imgs.getOther())
                .pickupDate(body.getPickupDate())
                .pickupSlotStart(body.getPickupSlotStart())
                .pickupSlotEnd(body.getPickupSlotEnd())
                .status("AWAITING_QUOTATION")
                .build());

        if (body.getScreeningAnswers() != null) for (ScreeningAnswerRow a : body.getScreeningAnswers()) {
            answerRepo.save(SellOrderScreeningAnswer.builder()
                    .sellOrderId(saved.getId()).questionId(a.getQuestionId())
                    .question(a.getQuestion()).answer(a.getAnswer()).build());
        }
        if (body.getConditions() != null) for (ConditionRow c : body.getConditions()) {
            conditionRepo.save(SellOrderCondition.builder()
                    .sellOrderId(saved.getId()).groupCode(c.getGroupCode()).groupName(c.getGroupName())
                    .optionId(c.getOptionId()).optionLabel(c.getOptionLabel()).build());
        }
        if (body.getIssues() != null) for (IssueRow i : body.getIssues()) {
            issueRepo.save(SellOrderIssue.builder()
                    .sellOrderId(saved.getId()).issueId(i.getIssueId()).issueCode(i.getIssueCode()).build());
        }
        if (body.getAccessories() != null) for (AccessoryRow a : body.getAccessories()) {
            accRepo.save(SellOrderAccessory.builder()
                    .sellOrderId(saved.getId()).accessoryId(a.getAccessoryId())
                    .accessoryCode(a.getAccessoryCode()).label(a.getLabel()).build());
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Sell Device");
        payload.put("sellOrderId", saved.getId());
        payload.put("brandId", body.getBrandId());
        payload.put("modelId", body.getModelId());
        payload.put("workingCondition", body.getWorkingCondition());
        String payloadJson;
        try { payloadJson = objectMapper.writeValueAsString(payload); }
        catch (Exception e) { payloadJson = null; }
        customerOrderRepo.save(CustomerOrder.builder()
                .orderNumber(sellNumber)
                .customerUserId(userId)
                .orderType("SELL")
                .referenceId(saved.getId())
                .status("PENDING")
                .payloadJson(payloadJson)
                .build());

        return ResponseEntity.ok(toResponseWithQuotes(saved));
    }

    @GetMapping
    public ResponseEntity<List<SellOrderResponse>> list(HttpServletRequest req) {
        UUID userId = callerId(req);
        return ResponseEntity.ok(sellRepo.findByCustomerUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SellOrderResponse> get(HttpServletRequest req, @PathVariable UUID id) {
        UUID userId = callerId(req);
        SellOrder s = sellRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sell order not found"));
        if (!s.getCustomerUserId().equals(userId)) throw new ForbiddenException("Not your sell order");
        return ResponseEntity.ok(toDetails(s));
    }

    @PostMapping("/{id}/quotations")
    @Transactional
    public ResponseEntity<SellQuotationResponse> addQuote(@PathVariable UUID id, @RequestBody SellOrderQuotationRequest body) {
        if (!sellRepo.existsById(id)) throw new ResourceNotFoundException("Sell order not found");
        SellOrderQuotation q = quoteRepo.save(SellOrderQuotation.builder()
                .sellOrderId(id).shopId(body.getShopId())
                .shopName(body.getShopName()).shopPhone(body.getShopPhone()).shopCity(body.getShopCity())
                .quotationPrice(body.getQuotationPrice()).note(body.getNote()).status("PROPOSED").build());
        return ResponseEntity.ok(toQuoteResp(q));
    }

    @GetMapping("/{id}/quotations")
    public ResponseEntity<List<SellQuotationResponse>> listQuotes(@PathVariable UUID id) {
        return ResponseEntity.ok(quoteRepo.findBySellOrderId(id).stream().map(this::toQuoteResp).toList());
    }

    @PostMapping("/{id}/choose-quotation")
    @Transactional
    public ResponseEntity<SellOrderResponse> choose(HttpServletRequest req, @PathVariable UUID id, @RequestBody ChooseQuotationRequest body) {
        UUID userId = callerId(req);
        SellOrder s = sellRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sell order not found"));
        if (!s.getCustomerUserId().equals(userId)) throw new ForbiddenException("Not your sell order");
        List<SellOrderQuotation> quotes = quoteRepo.findBySellOrderId(id);
        SellOrderQuotation chosen = quotes.stream().filter(q -> q.getId().equals(body.getQuotationId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Quotation not found"));
        for (SellOrderQuotation q : quotes) {
            q.setStatus(q.getId().equals(chosen.getId()) ? "ACCEPTED" : "REJECTED");
            quoteRepo.save(q);
        }
        s.setShopId(chosen.getShopId());
        s.setFinalPrice(chosen.getQuotationPrice());
        s.setStatus("SELL_CONFIRMED");
        sellRepo.save(s);
        customerOrderRepo.findByOrderNumber(s.getSellNumber()).ifPresent(co -> {
            co.setStatus("CONFIRMED");
            co.setShopId(chosen.getShopId());
            co.setTotalAmount(chosen.getQuotationPrice());
            customerOrderRepo.save(co);
        });
        return ResponseEntity.ok(toResponseWithQuotes(s));
    }

    // Customer-side edit. Allowed only while the sell order is still
    // "editable" (no shop has committed to a quotation yet). Replaces device
    // fields and the child rows (screening answers / conditions / issues /
    // accessories) when those arrays are present in the body.
    private static final java.util.Set<String> EDITABLE_STATUSES = java.util.Set.of(
            "PENDING", "AWAITING_QUOTATION", "DRAFT"
    );

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<SellOrderResponse> update(HttpServletRequest req,
                                                    @PathVariable UUID id,
                                                    @RequestBody SellOrderRequest body) {
        UUID userId = callerId(req);
        SellOrder s = sellRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sell order not found"));
        if (!s.getCustomerUserId().equals(userId)) throw new ForbiddenException("Not your sell order");
        String statusUpper = s.getStatus() == null ? "" : s.getStatus().toUpperCase();
        if (!EDITABLE_STATUSES.contains(statusUpper)) {
            throw new ForbiddenException("This sell order can no longer be edited (status: " + statusUpper + ")");
        }

        // Replace only the fields the caller actually sent. UUID fields are
        // applied unconditionally when non-null; string fields treat empty
        // string as a clear (so the customer can wipe IMEI/color).
        if (body.getBrandId() != null) s.setBrandId(body.getBrandId());
        if (body.getModelId() != null) s.setModelId(body.getModelId());
        if (body.getRamOptionId() != null) s.setRamOptionId(body.getRamOptionId());
        if (body.getStorageOptionId() != null) s.setStorageOptionId(body.getStorageOptionId());
        if (body.getAddressId() != null) s.setAddressId(body.getAddressId());
        if (body.getColor() != null) s.setColor(body.getColor().isBlank() ? null : body.getColor());
        if (body.getImei() != null) s.setImei(body.getImei().isBlank() ? null : body.getImei());
        if (body.getWorkingCondition() != null) s.setWorkingCondition(body.getWorkingCondition());
        if (body.getWarrantyCode() != null) s.setWarrantyCode(body.getWarrantyCode());
        if (body.getPickupDate() != null) s.setPickupDate(body.getPickupDate());
        if (body.getPickupSlotStart() != null) s.setPickupSlotStart(body.getPickupSlotStart());
        if (body.getPickupSlotEnd() != null) s.setPickupSlotEnd(body.getPickupSlotEnd());

        ImageBundle imgs = body.getImages();
        if (imgs != null) {
            if (imgs.getFront() != null) s.setFrontImageUrl(imgs.getFront().isBlank() ? null : imgs.getFront());
            if (imgs.getBack() != null) s.setBackImageUrl(imgs.getBack().isBlank() ? null : imgs.getBack());
            if (imgs.getSide() != null) s.setSideImageUrl(imgs.getSide().isBlank() ? null : imgs.getSide());
            if (imgs.getCamera() != null) s.setCameraImageUrl(imgs.getCamera().isBlank() ? null : imgs.getCamera());
            if (imgs.getOther() != null) s.setOtherImageUrl(imgs.getOther().isBlank() ? null : imgs.getOther());
        }

        sellRepo.save(s);

        // Replace child collections only when the caller explicitly sent them.
        if (body.getScreeningAnswers() != null) {
            answerRepo.deleteBySellOrderId(s.getId());
            for (ScreeningAnswerRow a : body.getScreeningAnswers()) {
                answerRepo.save(SellOrderScreeningAnswer.builder()
                        .sellOrderId(s.getId()).questionId(a.getQuestionId())
                        .question(a.getQuestion()).answer(a.getAnswer()).build());
            }
        }
        if (body.getConditions() != null) {
            conditionRepo.deleteBySellOrderId(s.getId());
            for (ConditionRow c : body.getConditions()) {
                conditionRepo.save(SellOrderCondition.builder()
                        .sellOrderId(s.getId()).groupCode(c.getGroupCode()).groupName(c.getGroupName())
                        .optionId(c.getOptionId()).optionLabel(c.getOptionLabel()).build());
            }
        }
        if (body.getIssues() != null) {
            issueRepo.deleteBySellOrderId(s.getId());
            for (IssueRow i : body.getIssues()) {
                issueRepo.save(SellOrderIssue.builder()
                        .sellOrderId(s.getId()).issueId(i.getIssueId()).issueCode(i.getIssueCode()).build());
            }
        }
        if (body.getAccessories() != null) {
            accRepo.deleteBySellOrderId(s.getId());
            for (AccessoryRow a : body.getAccessories()) {
                accRepo.save(SellOrderAccessory.builder()
                        .sellOrderId(s.getId()).accessoryId(a.getAccessoryId())
                        .accessoryCode(a.getAccessoryCode()).label(a.getLabel()).build());
            }
        }

        // Keep the matching customer_orders row consistent if anything that's
        // surfaced there changed (we only echo title-side fields here).
        customerOrderRepo.findByOrderNumber(s.getSellNumber()).ifPresent(co -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("title", "Sell Device");
            payload.put("sellOrderId", s.getId());
            payload.put("brandId", s.getBrandId());
            payload.put("modelId", s.getModelId());
            payload.put("workingCondition", s.getWorkingCondition());
            try { co.setPayloadJson(objectMapper.writeValueAsString(payload)); }
            catch (Exception ignored) {}
            customerOrderRepo.save(co);
        });

        return ResponseEntity.ok(toDetails(s));
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<SellOrderResponse> cancel(HttpServletRequest req, @PathVariable UUID id) {
        UUID userId = callerId(req);
        SellOrder s = sellRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sell order not found"));
        if (!s.getCustomerUserId().equals(userId)) throw new ForbiddenException("Not your sell order");
        s.setStatus("CANCELLED");
        sellRepo.save(s);
        customerOrderRepo.findByOrderNumber(s.getSellNumber()).ifPresent(co -> {
            co.setStatus("CANCELLED");
            customerOrderRepo.save(co);
        });
        return ResponseEntity.ok(toResponseWithQuotes(s));
    }

    private SellOrderResponse toResponse(SellOrder s) {
        return SellOrderResponse.builder()
                .id(s.getId()).sellNumber(s.getSellNumber()).shopId(s.getShopId()).addressId(s.getAddressId())
                .brandId(s.getBrandId()).modelId(s.getModelId())
                .ramOptionId(s.getRamOptionId()).storageOptionId(s.getStorageOptionId())
                .color(s.getColor()).imei(s.getImei())
                .workingCondition(s.getWorkingCondition()).warrantyCode(s.getWarrantyCode())
                .status(s.getStatus()).finalPrice(s.getFinalPrice())
                .pickupDate(s.getPickupDate()).pickupSlotStart(s.getPickupSlotStart()).pickupSlotEnd(s.getPickupSlotEnd())
                .createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt())
                .build();
    }

    private SellOrderResponse toResponseWithQuotes(SellOrder s) {
        SellOrderResponse r = toResponse(s);
        r.setQuotations(quoteRepo.findBySellOrderId(s.getId()).stream().map(this::toQuoteResp).toList());
        return r;
    }

    private SellOrderResponse toDetails(SellOrder s) {
        SellOrderResponse r = toResponseWithQuotes(s);
        r.setDeviceConditionSummary(s.getDeviceConditionSummary());
        r.setImages(ImageBundle.builder()
                .front(s.getFrontImageUrl()).back(s.getBackImageUrl()).side(s.getSideImageUrl())
                .camera(s.getCameraImageUrl()).other(s.getOtherImageUrl()).build());
        r.setScreeningAnswers(answerRepo.findBySellOrderId(s.getId()).stream()
                .map(a -> ScreeningAnswerRow.builder()
                        .questionId(a.getQuestionId()).question(a.getQuestion()).answer(a.getAnswer()).build())
                .toList());
        r.setConditions(conditionRepo.findBySellOrderId(s.getId()).stream()
                .map(c -> ConditionRow.builder()
                        .groupCode(c.getGroupCode()).groupName(c.getGroupName())
                        .optionId(c.getOptionId()).optionLabel(c.getOptionLabel()).build())
                .toList());
        r.setIssues(issueRepo.findBySellOrderId(s.getId()).stream()
                .map(i -> IssueRow.builder().issueId(i.getIssueId()).issueCode(i.getIssueCode()).build())
                .toList());
        r.setAccessories(accRepo.findBySellOrderId(s.getId()).stream()
                .map(a -> AccessoryRow.builder()
                        .accessoryId(a.getAccessoryId()).accessoryCode(a.getAccessoryCode()).label(a.getLabel()).build())
                .toList());
        return r;
    }

    private SellQuotationResponse toQuoteResp(SellOrderQuotation q) {
        return SellQuotationResponse.builder()
                .id(q.getId()).sellOrderId(q.getSellOrderId()).shopId(q.getShopId())
                .shopName(q.getShopName()).shopPhone(q.getShopPhone()).shopCity(q.getShopCity())
                .quotationPrice(q.getQuotationPrice()).note(q.getNote()).status(q.getStatus()).createdAt(q.getCreatedAt())
                .build();
    }

    private String uniqueSellNumber() {
        for (int i = 0; i < 10; i++) {
            StringBuilder sb = new StringBuilder("#CSPQX");
            for (int j = 0; j < 8; j++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            String c = sb.toString();
            if (sellRepo.findBySellNumber(c).isEmpty()) return c;
        }
        throw new IllegalStateException("Could not generate unique sell number");
    }

    private UUID callerId(HttpServletRequest req) {
        Object u = req.getAttribute("userId");
        if (u == null) throw new ForbiddenException("Missing userId");
        return UUID.fromString(u.toString());
    }
}
