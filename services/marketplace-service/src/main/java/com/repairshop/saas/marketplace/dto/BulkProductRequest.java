package com.repairshop.saas.marketplace.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * POST /shop/marketplace/products/bulk. Items stay raw JSON so one item with
 * a bad UUID becomes outcome=FAILED instead of a 400 for the whole batch.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkProductRequest {
    private String batchId;
    private Boolean publish;
    private List<JsonNode> items;
}
