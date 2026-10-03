package com.repairshop.saas.marketplace.service;

import com.repairshop.saas.marketplace.dto.ProductResponse;
import com.repairshop.saas.marketplace.entity.MarketplaceProduct;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility for mapping MarketplaceProduct entity to ProductResponse DTO.
 * Handles parsing of extra_image_urls (comma- or newline-separated string)
 * into a List<String>.
 */
public final class ProductMapper {

    private ProductMapper() {}

    public static ProductResponse toResponse(MarketplaceProduct p) {
        if (p == null) return null;
        return ProductResponse.builder()
                .id(p.getId())
                .shopId(p.getShopId())
                .sellerUserId(p.getSellerUserId())
                .brandId(p.getBrandId())
                .modelId(p.getModelId())
                .ramOptionId(p.getRamOptionId())
                .storageOptionId(p.getStorageOptionId())
                .title(p.getTitle())
                .description(p.getDescription())
                .type(p.getType())
                .price(p.getPrice())
                .status(p.getStatus())
                .conditionLabel(p.getConditionLabel())
                .color(p.getColor())
                .ramLabel(p.getRamLabel())
                .storageLabel(p.getStorageLabel())
                .network(p.getNetwork())
                .imei(p.getImei())
                .workingCondition(p.getWorkingCondition())
                .descriptionType(p.getDescriptionType())
                .imageUrl(p.getImageUrl())
                .extraImageUrls(parseExtraImages(p.getExtraImageUrls()))
                .assessmentJson(p.getAssessmentJson())
                .build();
    }

    public static List<String> parseExtraImages(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String trimmed = raw.trim();
        // Support JSON arrays: ["a","b"] -> strip brackets+quotes
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return Arrays.stream(trimmed.split("[,\\n]"))
                .map(String::trim)
                .map(s -> s.replaceAll("^[\"']|[\"']$", ""))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public static String serializeExtraImages(List<String> urls) {
        if (urls == null || urls.isEmpty()) return null;
        return urls.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(","));
    }
}
