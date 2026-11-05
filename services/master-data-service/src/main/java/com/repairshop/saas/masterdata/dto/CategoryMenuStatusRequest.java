package com.repairshop.saas.masterdata.dto;

/** Body of {@code PATCH /master/category-menu/{id}/status} — touches only isActive. */
public record CategoryMenuStatusRequest(Boolean isActive) {
}
