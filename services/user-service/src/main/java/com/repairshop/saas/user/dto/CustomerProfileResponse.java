package com.repairshop.saas.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerProfileResponse {

    private UUID id;
    private String fullName;
    private String email;
    private String mobile;
    private String alternateMobile;
    private String profileImageUrl;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
