package com.repairshop.saas.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public class SellOrderDtos {

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ScreeningAnswerRow {
        private UUID questionId;
        private String question;
        private String answer;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ConditionRow {
        private String groupCode;
        private String groupName;
        private UUID optionId;
        private String optionLabel;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class IssueRow {
        private UUID issueId;
        private String issueCode;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AccessoryRow {
        private UUID accessoryId;
        private String accessoryCode;
        private String label;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ImageBundle {
        private String front;
        private String back;
        private String side;
        private String camera;
        private String other;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SellOrderRequest {
        private UUID brandId;
        private UUID modelId;
        private UUID ramOptionId;
        private UUID storageOptionId;
        private String color;
        private String imei;
        private String workingCondition; // WORKING | DEAD | UNKNOWN
        private String warrantyCode;
        private UUID addressId;
        private LocalDate pickupDate;
        private LocalTime pickupSlotStart;
        private LocalTime pickupSlotEnd;
        private List<ScreeningAnswerRow> screeningAnswers;
        private List<ConditionRow> conditions;
        private List<IssueRow> issues;
        private List<AccessoryRow> accessories;
        private ImageBundle images;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SellQuotationResponse {
        private UUID id;
        private UUID sellOrderId;
        private UUID shopId;
        private String shopName;
        private String shopPhone;
        private String shopCity;
        private BigDecimal quotationPrice;
        private String note;
        private String status;
        private Instant createdAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SellOrderQuotationRequest {
        private UUID shopId;
        private String shopName;
        private String shopPhone;
        private String shopCity;
        private BigDecimal quotationPrice;
        private String note;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ChooseQuotationRequest {
        private UUID quotationId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SellOrderResponse {
        private UUID id;
        private String sellNumber;
        private UUID shopId;
        private UUID addressId;
        private UUID brandId;
        private UUID modelId;
        private UUID ramOptionId;
        private UUID storageOptionId;
        private String color;
        private String imei;
        private String workingCondition;
        private String warrantyCode;
        private String status;
        private BigDecimal finalPrice;
        private LocalDate pickupDate;
        private LocalTime pickupSlotStart;
        private LocalTime pickupSlotEnd;
        private List<SellQuotationResponse> quotations;
        private String deviceConditionSummary;
        private ImageBundle images;
        private List<ScreeningAnswerRow> screeningAnswers;
        private List<ConditionRow> conditions;
        private List<IssueRow> issues;
        private List<AccessoryRow> accessories;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
