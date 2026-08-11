package com.repairshop.saas.ticket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.repairshop.saas.ticket.dto.LedgerPartyDtos.LedgerPartyResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Wire shapes for the customer / supplier running account (see migration 82). */
public class LedgerEntryDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Record money received from, or given to, a party")
    public static class LedgerEntryRequest {

        @Schema(description = "RECEIVED (party paid the shop) or GIVEN (shop paid the party)", example = "RECEIVED")
        private String direction;

        @Schema(description = "Amount in rupees, always positive", example = "1500.00")
        private BigDecimal amount;

        @Schema(description = "Counter day the entry belongs to; defaults to today (IST)")
        private LocalDate entryDate;

        @Schema(description = "What it was for", example = "samsung s8+")
        private String note;

        /**
         * A spoken note, already uploaded to media storage. Independent of
         * {@code note} — a counter is a place where talking beats typing, so an
         * entry may carry a voice note and no text at all.
         */
        @Schema(description = "URL of a recorded voice note",
                example = "https://media.ggfix.in/cashbook/voice-3f9c11ab.m4a")
        private String noteAudioUrl;

        /**
         * Photographed bills, already uploaded. On update null means "leave the
         * existing bills alone" and an empty list means "remove them" — the same
         * rule the rest of this request follows.
         */
        @Schema(description = "URLs of the photographed bills backing this entry")
        private List<String> billUrls;

        /**
         * The repair this money was paid against, when it was one — the "advance
         * on a job in progress" case. The server re-reads the ticket to snapshot
         * its label, so the two below are never trusted from the client.
         */
        @Schema(description = "Ticket this entry is an advance / payment against")
        private UUID ticketId;

        @Schema(description = "Ignored on write — the server snapshots it from the ticket", accessMode = Schema.AccessMode.READ_ONLY)
        private String ticketTrackingId;

        @Schema(description = "Ignored on write — the server snapshots it from the ticket", accessMode = Schema.AccessMode.READ_ONLY)
        private String ticketLabel;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "A single entry on a party's account")
    public static class LedgerEntryResponse {
        private UUID id;
        private UUID partyId;

        /**
         * Carried on the row because the Today / This Week / Month feed mixes
         * every party together, where an entry with no name on it is unreadable.
         */
        private String partyName;

        private String direction;
        private BigDecimal amount;
        private LocalDate entryDate;
        private String note;
        private String noteAudioUrl;

        /**
         * Always present, empty when the entry has no bills — the statement
         * renders a thumbnail strip off this and shouldn't have to null-check.
         * (@JsonInclude(NON_NULL) on this class would otherwise drop the key.)
         */
        private List<String> billUrls;

        /** The repair this was paid against, with its label snapshotted (84). */
        private UUID ticketId;
        private String ticketTrackingId;
        private String ticketLabel;

        /**
         * The account's balance AFTER this entry, oldest-to-newest. This is the
         * "₹4,000 Advance" line under each row of the statement — computing it
         * here keeps the client from having to replay the whole ledger to render
         * one screen, and keeps the two from ever disagreeing.
         */
        private BigDecimal runningBalance;

        private Instant createdAt;
    }

    /** One account's statement: who they are, where they stand, what happened. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "A party's account with its entries")
    public static class LedgerStatementResponse {
        private LedgerPartyResponse party;

        @Schema(description = "+ is Due (they owe the shop), − is Advance", example = "-4000.00")
        private BigDecimal balance;

        private BigDecimal totalReceived;
        private BigDecimal totalGiven;
        private List<LedgerEntryResponse> entries;
    }

    /** Every party's movements over a date window — the period chips. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "All ledger movements in a date window")
    public static class LedgerPeriodResponse {
        private LocalDate from;
        private LocalDate to;
        private BigDecimal totalReceived;
        private BigDecimal totalGiven;
        private List<LedgerEntryResponse> entries;
    }
}
