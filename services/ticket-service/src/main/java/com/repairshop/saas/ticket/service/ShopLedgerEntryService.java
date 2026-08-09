package com.repairshop.saas.ticket.service;

import com.repairshop.saas.ticket.dto.LedgerEntryDtos.LedgerEntryRequest;
import com.repairshop.saas.ticket.dto.LedgerEntryDtos.LedgerEntryResponse;
import com.repairshop.saas.ticket.dto.LedgerEntryDtos.LedgerPeriodResponse;
import com.repairshop.saas.ticket.dto.LedgerEntryDtos.LedgerStatementResponse;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The running account between the shop and one named customer or supplier.
 *
 * Balances are NEVER stored — every figure here is summed from the entries, so
 * editing, back-dating or deleting one can't leave a stale total behind. This is
 * money a shop argues with a customer about; a figure that can drift is worse
 * than one that costs a GROUP BY.
 */
@Service
@RequiredArgsConstructor
public class ShopLedgerEntryService {

    /**
     * A counter day is a local calendar day. The JVM on EC2 runs UTC, so an
     * entry saved after 5:30am IST would otherwise be filed under the wrong day
     * whenever the client omits entryDate — the same trap that shifted employee
     * attendance by ~5.5 hours.
     */
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final Set<String> DIRECTIONS = Set.of("RECEIVED", "GIVEN");
    private static final int MAX_WINDOW_DAYS = 366;
    private static final int MAX_NOTE = 500;

    private final ShopLedgerEntryRepository repository;
    private final ShopLedgerPartyRepository partyRepository;

    // ---- Reads -----------------------------------------------------------------

    /** One account's statement: who they are, where they stand, what happened. */
    @Transactional(readOnly = true)
    public LedgerStatementResponse statement(UUID shopId, UUID partyId) {
        ShopLedgerParty party = requireParty(shopId, partyId);
        List<ShopLedgerEntry> rows =
                repository.findByShopIdAndPartyIdOrderByEntryDateDescCreatedAtDesc(shopId, partyId);

        BigDecimal received = BigDecimal.ZERO;
        BigDecimal given = BigDecimal.ZERO;
        for (ShopLedgerEntry e : rows) {
            if ("GIVEN".equals(e.getDirection())) given = given.add(e.getAmount());
            else received = received.add(e.getAmount());
        }
        BigDecimal balance = given.subtract(received);

        return LedgerStatementResponse.builder()
                .party(ShopLedgerPartyService.decorate(party, balance, rows.isEmpty() ? null : rows.get(0)))
                .balance(balance)
                .totalReceived(received)
                .totalGiven(given)
                .entries(withRunningBalance(rows, party.getName()))
                .build();
    }

    /** Every party's movements over a date window — the Today/Week/Month chips. */
    @Transactional(readOnly = true)
    public LedgerPeriodResponse period(UUID shopId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(IST);
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must not be after `to`.");
        }
        // A window is one screen of a ledger, not an export. Without a bound a
        // single request could pull a shop's entire history into memory.
        if (start.plusDays(MAX_WINDOW_DAYS).isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Date range is too wide — request at most " + MAX_WINDOW_DAYS + " days.");
        }

        List<ShopLedgerEntry> rows =
                repository.findByShopIdAndEntryDateBetweenOrderByEntryDateDescCreatedAtDesc(shopId, start, end);

        // One lookup for every name in the window. The feed mixes parties
        // together, so each row has to say who it belongs to, and resolving that
        // per row would be an N+1 over the whole period.
        Map<UUID, String> names = new HashMap<>();
        for (ShopLedgerParty p : partyRepository.findAllById(rows.stream().map(ShopLedgerEntry::getPartyId).toList())) {
            names.put(p.getId(), p.getName());
        }

        BigDecimal received = BigDecimal.ZERO;
        BigDecimal given = BigDecimal.ZERO;
        List<LedgerEntryResponse> out = new ArrayList<>(rows.size());
        for (ShopLedgerEntry e : rows) {
            if ("GIVEN".equals(e.getDirection())) given = given.add(e.getAmount());
            else received = received.add(e.getAmount());
            // No runningBalance in this feed: a running total only means
            // something within ONE account, and this list interleaves many.
            out.add(toResponse(e, names.get(e.getPartyId()), null));
        }

        return LedgerPeriodResponse.builder()
                .from(start)
                .to(end)
                .totalReceived(received)
                .totalGiven(given)
                .entries(out)
                .build();
    }

    // ---- Writes ----------------------------------------------------------------

    @Transactional
    public LedgerEntryResponse create(UUID shopId, UUID userId, UUID partyId, LedgerEntryRequest req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing request body");
        ShopLedgerParty party = requireParty(shopId, partyId);

        ShopLedgerEntry e = new ShopLedgerEntry();
        e.setShopId(shopId);
        e.setPartyId(partyId);
        e.setCreatedBy(userId);
        apply(e, req, true);

        return toResponse(repository.save(e), party.getName(), null);
    }

    /** Only the fields sent are changed; the entry never moves to another party. */
    @Transactional
    public LedgerEntryResponse update(UUID shopId, UUID id, LedgerEntryRequest req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing request body");
        ShopLedgerEntry e = repository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry not found"));
        apply(e, req, false);
        ShopLedgerParty party = requireParty(shopId, e.getPartyId());
        return toResponse(repository.save(e), party.getName(), null);
    }

    @Transactional
    public void delete(UUID shopId, UUID id) {
        ShopLedgerEntry e = repository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry not found"));
        repository.delete(e);
    }

    // ---- Helpers ---------------------------------------------------------------

    private ShopLedgerParty requireParty(UUID shopId, UUID partyId) {
        return partyRepository.findByIdAndShopId(partyId, shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    /**
     * Copies the request onto the entity. On create every defaultable field
     * falls back (today); on update a null field means "leave as it was", so a
     * partial edit can't blank out the rest of the row.
     */
    private void apply(ShopLedgerEntry e, LedgerEntryRequest req, boolean creating) {
        if (req.getDirection() != null || creating) {
            String dir = req.getDirection() != null ? req.getDirection().trim().toUpperCase() : "";
            if (!DIRECTIONS.contains(dir)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "direction must be RECEIVED or GIVEN");
            }
            e.setDirection(dir);
        }

        if (req.getAmount() != null || creating) {
            BigDecimal amount = req.getAmount();
            if (amount == null || amount.signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be greater than zero");
            }
            e.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        }

        if (req.getEntryDate() != null) {
            // A ledger is written up after the fact, so back-dating is normal;
            // future-dating is not — it would put money in an account before it
            // moved.
            if (req.getEntryDate().isAfter(LocalDate.now(IST))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "entryDate cannot be in the future");
            }
            e.setEntryDate(req.getEntryDate());
        } else if (creating) {
            e.setEntryDate(LocalDate.now(IST));
        }

        if (req.getNote() != null) {
            String note = req.getNote().trim();
            if (note.length() > MAX_NOTE) note = note.substring(0, MAX_NOTE);
            e.setNote(note.isBlank() ? null : note);
        }
    }

    /**
     * Stamps each row with the balance AFTER it.
     *
     * `rows` arrives newest-first (how the statement is read), but a running
     * balance only accumulates oldest-first — so it is walked backwards and the
     * newest-first order is preserved for the caller.
     */
    private static List<LedgerEntryResponse> withRunningBalance(List<ShopLedgerEntry> rows, String partyName) {
        LedgerEntryResponse[] out = new LedgerEntryResponse[rows.size()];
        BigDecimal running = BigDecimal.ZERO;
        for (int i = rows.size() - 1; i >= 0; i -= 1) {
            ShopLedgerEntry e = rows.get(i);
            running = "GIVEN".equals(e.getDirection())
                    ? running.add(e.getAmount())
                    : running.subtract(e.getAmount());
            out[i] = toResponse(e, partyName, running);
        }
        return List.of(out);
    }

    private static LedgerEntryResponse toResponse(ShopLedgerEntry e, String partyName, BigDecimal running) {
        return LedgerEntryResponse.builder()
                .id(e.getId())
                .partyId(e.getPartyId())
                .partyName(partyName)
                .direction(e.getDirection())
                .amount(e.getAmount())
                .entryDate(e.getEntryDate())
                .note(e.getNote())
                .runningBalance(running)
                .createdAt(e.getCreatedAt())
                .build();
    }
}
