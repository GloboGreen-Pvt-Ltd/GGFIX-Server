package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.ShopLedgerParty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShopLedgerPartyRepository extends JpaRepository<ShopLedgerParty, UUID> {

    /** One book of the address list — newest account first, as the app shows it. */
    List<ShopLedgerParty> findByShopIdAndPartyTypeOrderByCreatedAtDesc(UUID shopId, String partyType);

    /**
     * Resolves the unique (shop, type, phone) account. Import-from-contacts hits
     * this first so re-importing the same number updates the existing account
     * instead of tripping the unique index with a 500.
     */
    Optional<ShopLedgerParty> findByShopIdAndPartyTypeAndPhone(UUID shopId, String partyType, String phone);

    /**
     * Shop-scoped lookup. Update/delete resolve through this rather than
     * findById so one shop can never touch another shop's row by guessing an id.
     */
    Optional<ShopLedgerParty> findByIdAndShopId(UUID id, UUID shopId);
}
