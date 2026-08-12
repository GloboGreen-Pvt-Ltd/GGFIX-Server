package com.repairshop.saas.ticket.service;

import com.repairshop.saas.ticket.dto.LedgerPartyDtos.LedgerPartyRequest;
import com.repairshop.saas.ticket.dto.LedgerPartyDtos.LedgerPartyResponse;
import com.repairshop.saas.ticket.entity.ShopLedgerEntry;
import com.repairshop.saas.ticket.entity.ShopLedgerParty;
import com.repairshop.saas.ticket.repository.ShopLedgerEntryRepository;
import com.repairshop.saas.ticket.repository.ShopLedgerPartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The shop's customer and supplier accounts — the address book behind the Cash
 * Book screen's Customer / Supplier tabs.
 */
@Service
@RequiredArgsConstructor
public class ShopLedgerPartyService {

    private static final Set<String> PARTY_TYPES = Set.of("CUSTOMER", "SUPPLIER");
    private static final int PHONE_DIGITS = 10;
    private static final int MAX_NAME = 120;

    private final ShopLedgerPartyRepository repository;
    private final ShopLedgerEntryRepository entryRepository;

    // ---- Reads -----------------------------------------------------------------

    /**
     * The account list, each row carrying its balance and last movement.
     *
     * Balances and last-entries are fetched as TWO bulk queries and joined in
     * memory rather than resolved per row: the list is the screen a shop opens
     * every morning, and a per-party query would be an N+1 that grows with the
     * address book.
     */
    @Transactional(readOnly = true)
    public List<LedgerPartyResponse> list(UUID shopId, String partyType) {
        List<ShopLedgerParty> rows =
                repository.findByShopIdAndPartyTypeOrderByCreatedAtDesc(shopId, requireType(partyType));
        if (rows.isEmpty()) return List.of();

        Map<UUID, BigDecimal> balances = new HashMap<>();
        for (Object[] row : entryRepository.balancesByParty(shopId)) {
            balances.put((UUID) row[0], toDecimal(row[1]));
        }

        Map<UUID, ShopLedgerEntry> latest = new HashMap<>();
        for (ShopLedgerEntry e : entryRepository.latestPerParty(shopId)) {
            latest.put(e.getPartyId(), e);
        }

        // Second pass for the same reason as the first: one query for the whole
        // book beats a per-party lookup that grows with the address book.
        Map<UUID, LocalDate> lastPaid = new HashMap<>();
        for (ShopLedgerEntry e : entryRepository.latestPaymentPerParty(shopId)) {
            lastPaid.put(e.getPartyId(), e.getEntryDate());
        }

        return rows.stream()
                .map(p -> decorate(p, balances.get(p.getId()), latest.get(p.getId()), lastPaid.get(p.getId())))
                .toList();
    }

    /** One account with its balance — the statement header. */
    @Transactional(readOnly = true)
    public LedgerPartyResponse get(UUID shopId, UUID id) {
        ShopLedgerParty p = repository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        return decorate(p, entryRepository.balanceForParty(shopId, id), null);
    }

    // ---- Writes ----------------------------------------------------------------

    /**
     * Adds an account, or updates the name on the one that already holds this
     * number.
     *
     * The upsert is not a convenience: importing from the phone's contact list is
     * the primary way rows are created here, and a contact book is full of the
     * same number under two entries. Without it the second import would hit the
     * unique index and surface as a 500 the owner can do nothing about.
     */
    @Transactional
    public LedgerPartyResponse create(UUID shopId, LedgerPartyRequest req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing request body");

        String type = requireType(req.getPartyType());
        String phone = requirePhone(req.getPhone());
        // Falls back to the number so the list always has something to render —
        // this is the "8939615914" row in the design, a number jotted down with
        // no name attached.
        String name = normalizeName(req.getName(), phone);

        Optional<ShopLedgerParty> existing = repository.findByShopIdAndPartyTypeAndPhone(shopId, type, phone);
        if (existing.isPresent()) {
            ShopLedgerParty p = existing.get();
            p.setName(name);
            return toResponse(repository.save(p));
        }

        ShopLedgerParty p = new ShopLedgerParty();
        p.setShopId(shopId);
        p.setPartyType(type);
        p.setPhone(phone);
        p.setName(name);
        return toResponse(repository.save(p));
    }

    /** Only the fields sent are changed; partyType is fixed at creation. */
    @Transactional
    public LedgerPartyResponse update(UUID shopId, UUID id, LedgerPartyRequest req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing request body");

        ShopLedgerParty p = repository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (req.getPhone() != null) {
            String phone = requirePhone(req.getPhone());
            if (!phone.equals(p.getPhone())) {
                // Moving a number onto an account that already holds it would
                // break the unique index; say so rather than 500.
                repository.findByShopIdAndPartyTypeAndPhone(shopId, p.getPartyType(), phone)
                        .filter(other -> !other.getId().equals(p.getId()))
                        .ifPresent(other -> {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "Another account already uses this number.");
                        });
                p.setPhone(phone);
            }
        }

        if (req.getName() != null) p.setName(normalizeName(req.getName(), p.getPhone()));

        return toResponse(repository.save(p));
    }

    @Transactional
    public void delete(UUID shopId, UUID id) {
        ShopLedgerParty p = repository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        repository.delete(p);
    }

    // ---- Helpers ---------------------------------------------------------------

    private static String requireType(String raw) {
        String type = raw != null ? raw.trim().toUpperCase() : "";
        if (!PARTY_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "partyType must be CUSTOMER or SUPPLIER");
        }
        return type;
    }

    /**
     * Digits only, in the 10-digit national form the rest of the platform stores.
     * Contacts come out of the phone book written every possible way
     * ("+91 98765 43210", "098765-43210"), and the unique index is only a real
     * guard if they all collapse to the same string.
     */
    private static String requirePhone(String raw) {
        String digits = raw != null ? raw.replaceAll("\\D", "") : "";
        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        else if (digits.length() == 11 && digits.startsWith("0")) digits = digits.substring(1);

        if (digits.length() != PHONE_DIGITS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid 10-digit phone number");
        }
        return digits;
    }

    private static String normalizeName(String raw, String phoneFallback) {
        String name = raw != null ? raw.trim().replaceAll("\\s+", " ") : "";
        if (name.isBlank()) return phoneFallback;
        return name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
    }

    /** A freshly created or renamed account — nothing has moved on it yet. */
    static LedgerPartyResponse toResponse(ShopLedgerParty p) {
        return decorate(p, null, null);
    }

    /**
     * `balance` is always present, as ZERO rather than null, on an account with
     * no entries. A null would force every caller to decide what "no balance"
     * renders as, and a new account is not missing a balance — it is settled.
     */
    static LedgerPartyResponse decorate(ShopLedgerParty p, BigDecimal balance, ShopLedgerEntry last) {
        return decorate(p, balance, last, null);
    }

    /**
     * As above, plus when the account last paid. Only the list needs that — the
     * single-account reads have the full entry history to hand — so the
     * three-argument form stays the one most callers use.
     */
    static LedgerPartyResponse decorate(
            ShopLedgerParty p, BigDecimal balance, ShopLedgerEntry last, LocalDate lastPaymentDate) {
        return LedgerPartyResponse.builder()
                .lastPaymentDate(lastPaymentDate)
                .id(p.getId())
                .partyType(p.getPartyType())
                .name(p.getName())
                .phone(p.getPhone())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .balance(balance != null ? balance : BigDecimal.ZERO)
                .lastEntryDirection(last != null ? last.getDirection() : null)
                .lastEntryAmount(last != null ? last.getAmount() : null)
                .lastEntryDate(last != null ? last.getEntryDate() : null)
                .build();
    }

    /**
     * JPQL's COALESCE(SUM(...)) is typed by the provider, not by us — Hibernate
     * has been known to hand back a Long for the literal 0 branch. Narrowing
     * through Number keeps the sum correct whichever it picks.
     */
    private static BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal d) return d;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
