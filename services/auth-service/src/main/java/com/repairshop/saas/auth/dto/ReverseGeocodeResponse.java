package com.repairshop.saas.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** success=false never carries a stack trace or provider detail — just a clean message to show or fall back on. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReverseGeocodeResponse {
    private boolean success;
    private String message;
    private CustomerLocationView location;
}
