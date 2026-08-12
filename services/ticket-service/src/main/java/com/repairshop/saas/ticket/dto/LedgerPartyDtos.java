package com.repairshop.saas.ticket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Wire shapes for the shop's customer / supplier accounts (see migration 81). */
public class LedgerPartyDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Create or update a customer / supplier account")
    public static class LedgerPartyRequest {

        @Schema(description = "CUSTOMER or SUPPLIER; ignored on update", example = "CUSTOMER")
        private String partyType;

        @Schema(description = "Display name; defaults to the phone number when blank", example = "Sakthi Mobile")
        private String name;

        @Schema(description = "10-digit Indian mobile number", example = "9787727207")
        private String phone;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "A customer or supplier account")
    public static class LedgerPartyResponse {
        private UUID id;
        private String partyType;
        private String name;
        private String phone;
        private Instant createdAt;
        private Instant updatedAt;

        /**
         * Derived from shop_ledger_entries, never stored: positive means the
         * party owes the shop ("Due"), negative that they have paid ahead
         * ("Advance"), zero that the account is settled.
         */
        @Schema(description = "SUM(GIVEN) − SUM(RECEIVED); + is Due, − is Advance", example = "-4000.00")
        private BigDecimal balance;

        // The last movement on the account, for the list subtitle. All null on
        // an account that has never been transacted on — the app falls back to
        // "Added On <created date>" for those.
        private String lastEntryDirection;
        private BigDecimal lastEntryAmount;
        private LocalDate lastEntryDate;

        /**
         * The last RECEIVED entry's date — when this account last actually paid.
         *
         * Distinct from lastEntryDate, which is the last movement in either
         * direction: an account that paid on Monday and was given fresh credit
         * on Tuesday has a lastEntryDate of Tuesday and a lastPaymentDate of
         * Monday. The app's "Last Payment" sort needs the second one, and
         * cannot derive it from the first. Null until the account has paid once.
         */
        @Schema(description = "Date of the most recent RECEIVED entry", example = "2026-08-10")
        private LocalDate lastPaymentDate;
    }
}
