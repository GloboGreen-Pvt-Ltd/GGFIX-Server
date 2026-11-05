package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.ShopLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShopLedgerEntryRepository extends JpaRepository<ShopLedgerEntry, UUID> {

    /** One account's statement, newest first. */
    List<ShopLedgerEntry> findByShopIdAndPartyIdOrderByEntryDateDescCreatedAtDesc(UUID shopId, UUID partyId);

    /** The Today / This Week / Month feed — every party, one date window. */
    List<ShopLedgerEntry> findByShopIdAndEntryDateBetweenOrderByEntryDateDescCreatedAtDesc(
            UUID shopId, LocalDate from, LocalDate to);

    /**
     * Every account's balance in ONE query.
     *
     * The account list needs a figure per row; asking per party would be an N+1
     * that grows with the shop's address book. GIVEN is positive because the
     * app reads a positive balance as "Due" — the party owes the shop.
     *
     * Returns [partyId, balance] pairs. Parties with no entries are simply
     * absent, which the service reads as zero.
     */
    @Query("""
           SELECT e.partyId,
                  COALESCE(SUM(CASE WHEN e.direction = 'GIVEN' THEN e.amount ELSE -e.amount END), 0)
           FROM ShopLedgerEntry e
           WHERE e.shopId = :shopId
           GROUP BY e.partyId
           """)
    List<Object[]> balancesByParty(@Param("shopId") UUID shopId);

    /** Balance for a single account, for the statement header. */
    @Query("""
           SELECT COALESCE(SUM(CASE WHEN e.direction = 'GIVEN' THEN e.amount ELSE -e.amount END), 0)
           FROM ShopLedgerEntry e
           WHERE e.shopId = :shopId AND e.partyId = :partyId
           """)
    java.math.BigDecimal balanceForParty(@Param("shopId") UUID shopId, @Param("partyId") UUID partyId);

    /**
     * The most recent entry of every account, for the list subtitle
     * ("₹1,500 Received on 01 Aug").
     *
     * A correlated MAX subquery rather than Postgres' DISTINCT ON: the dev
     * profile runs H2, and a native query here would work in production and
     * fail on every developer's machine.
     */
    @Query("""
           SELECT e FROM ShopLedgerEntry e
           WHERE e.shopId = :shopId
             AND e.createdAt = (
                 SELECT MAX(x.createdAt) FROM ShopLedgerEntry x
                 WHERE x.shopId = :shopId AND x.partyId = e.partyId
             )
           """)
    List<ShopLedgerEntry> latestPerParty(@Param("shopId") UUID shopId);

    /**
     * The last time each account actually PAID, for the app's "Last Payment"
     * sort. Same correlated-MAX shape as latestPerParty (and H2-safe for the
     * same reason), narrowed to RECEIVED — an account that pays and is then
     * given fresh credit still sorts by the payment, not by the credit.
     */
    @Query("""
           SELECT e FROM ShopLedgerEntry e
           WHERE e.shopId = :shopId
             AND e.direction = 'RECEIVED'
             AND e.createdAt = (
                 SELECT MAX(x.createdAt) FROM ShopLedgerEntry x
                 WHERE x.shopId = :shopId AND x.partyId = e.partyId AND x.direction = 'RECEIVED'
             )
           """)
    List<ShopLedgerEntry> latestPaymentPerParty(@Param("shopId") UUID shopId);

    /**
     * Shop-scoped lookup. Update/delete resolve through this rather than
     * findById so one shop can never touch another shop's row by guessing an id.
     */
    Optional<ShopLedgerEntry> findByIdAndShopId(UUID id, UUID shopId);
}
