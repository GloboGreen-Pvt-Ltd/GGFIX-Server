package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Link a platform customer_users row into this shop's customers table")
public class CustomerLinkRequest {

    @NotNull
    @Schema(description = "Platform customer_users.id to link", required = true)
    private UUID platformUserId;
}
