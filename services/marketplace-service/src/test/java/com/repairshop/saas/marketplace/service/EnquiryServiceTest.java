package com.repairshop.saas.marketplace.service;

import com.repairshop.saas.marketplace.dto.EnquiryCreateRequest;
import com.repairshop.saas.marketplace.dto.EnquiryOfferRequest;
import com.repairshop.saas.marketplace.dto.EnquiryResponse;
import com.repairshop.saas.marketplace.entity.MarketplaceEnquiry;
import com.repairshop.saas.marketplace.entity.MarketplaceEnquiryOffer;
import com.repairshop.saas.marketplace.entity.MarketplaceProduct;
import com.repairshop.saas.marketplace.exception.BadRequestException;
import com.repairshop.saas.marketplace.exception.ConflictException;
import com.repairshop.saas.marketplace.exception.ResourceNotFoundException;
import com.repairshop.saas.marketplace.repository.CustomerChatThreadRepository;
import com.repairshop.saas.marketplace.repository.MarketplaceEnquiryOfferRepository;
import com.repairshop.saas.marketplace.repository.MarketplaceEnquiryRepository;
import com.repairshop.saas.marketplace.repository.MarketplaceProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Enquiry state machine against in-memory repositories (no database). */
class EnquiryServiceTest {

    private final MarketplaceEnquiryRepository enquiryRepo = mock(MarketplaceEnquiryRepository.class);
    private final MarketplaceEnquiryOfferRepository offerRepo = mock(MarketplaceEnquiryOfferRepository.class);
    private final MarketplaceProductRepository productRepo = mock(MarketplaceProductRepository.class);
    private final CustomerChatThreadRepository threadRepo = mock(CustomerChatThreadRepository.class);
    private final ChatSnapshotService snapshots = mock(ChatSnapshotService.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

    private final Map<UUID, MarketplaceEnquiry> enquiries = new LinkedHashMap<>();
    private final Map<UUID, MarketplaceProduct> products = new LinkedHashMap<>();
    private final List<MarketplaceEnquiryOffer> steps = new ArrayList<>();

    private final UUID shopId = UUID.randomUUID();
    private final UUID buyerA = UUID.randomUUID();
    private final UUID buyerB = UUID.randomUUID();
    private EnquiryService service;

    @BeforeEach
    void setUp() {
        service = new EnquiryService(enquiryRepo, offerRepo, productRepo, threadRepo, snapshots, txManager);

        when(enquiryRepo.saveAndFlush(any(MarketplaceEnquiry.class))).thenAnswer(inv -> storeEnquiry(inv.getArgument(0)));
        when(enquiryRepo.save(any(MarketplaceEnquiry.class))).thenAnswer(inv -> storeEnquiry(inv.getArgument(0)));
        when(enquiryRepo.findByIdAndShopId(any(), any())).thenAnswer(inv -> Optional.ofNullable(enquiries.get(inv.<UUID>getArgument(0)))
                .filter(e -> e.getShopId().equals(inv.getArgument(1))));
        when(enquiryRepo.findByIdAndBuyerUserId(any(), any())).thenAnswer(inv -> Optional.ofNullable(enquiries.get(inv.<UUID>getArgument(0)))
                .filter(e -> e.getBuyerUserId().equals(inv.getArgument(1))));
        when(enquiryRepo.findFirstByProductIdAndBuyerUserIdAndStatusInOrderByCreatedAtDesc(any(), any(), any()))
                .thenAnswer(inv -> enquiries.values().stream()
                        .filter(e -> e.getProductId().equals(inv.getArgument(0)) && e.getBuyerUserId().equals(inv.getArgument(1))
                                && inv.<Collection<String>>getArgument(2).contains(e.getStatus()))
                        .findFirst());
        when(enquiryRepo.findByProductIdAndStatusIn(any(), any())).thenAnswer(inv -> enquiries.values().stream()
                .filter(e -> e.getProductId().equals(inv.getArgument(0)) && inv.<Collection<String>>getArgument(1).contains(e.getStatus()))
                .toList());
        when(offerRepo.save(any(MarketplaceEnquiryOffer.class))).thenAnswer(inv -> {
            MarketplaceEnquiryOffer o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            steps.add(o);
            return o;
        });
        when(offerRepo.findByEnquiryIdOrderByCreatedAtAsc(any())).thenAnswer(inv -> steps.stream()
                .filter(o -> o.getEnquiryId().equals(inv.getArgument(0))).toList());
        when(productRepo.findById(any())).thenAnswer(inv -> Optional.ofNullable(products.get(inv.<UUID>getArgument(0))));
        when(productRepo.findByIdForUpdate(any())).thenAnswer(inv -> Optional.ofNullable(products.get(inv.<UUID>getArgument(0))));
        when(productRepo.save(any(MarketplaceProduct.class))).thenAnswer(inv -> inv.getArgument(0));
        when(threadRepo.findByCustomerUserIdAndShopId(any(), any())).thenReturn(Optional.empty());
        when(snapshots.loadCustomer(any())).thenReturn(new ChatSnapshotService.CustomerSnapshot("Ravi", "9876543210", null, null));
    }

    private MarketplaceEnquiry storeEnquiry(MarketplaceEnquiry e) {
        if (e.getId() == null) e.setId(UUID.randomUUID());
        enquiries.put(e.getId(), e);
        return e;
    }

    private MarketplaceProduct product(int quantity, String fulfilment) {
        MarketplaceProduct p = MarketplaceProduct.builder()
                .id(UUID.randomUUID()).shopId(shopId).title("iPhone 13").type("SELL").status("ACTIVE")
                .price(new BigDecimal("10000")).quantity(quantity).soldQuantity(0).negotiable(true)
                .listingType("DEVICE").fulfilmentOptions(fulfilment).build();
        products.put(p.getId(), p);
        return p;
    }

    private EnquiryResponse ask(UUID buyer, MarketplaceProduct p, String kind, String offer) {
        EnquiryCreateRequest req = new EnquiryCreateRequest(kind, "Is it available?",
                offer == null ? null : new BigDecimal(offer), null);
        return service.create(buyer, null, p.getId(), req).enquiry();
    }

    private static EnquiryOfferRequest offer(String amount) {
        return new EnquiryOfferRequest(new BigDecimal(amount), null);
    }

    @Test
    void offerCounterAcceptConfirmDecrementsStock() {
        MarketplaceProduct p = product(2, "PICKUP,SHIPPING");

        EnquiryResponse e = ask(buyerA, p, "PURCHASE_REQUEST", "8000");
        assertEquals("OPEN", e.getStatus());
        assertEquals("BUYER", e.getLastOfferBy());
        assertEquals("Ravi", e.getBuyer().getName());
        UUID id = e.getId();

        EnquiryResponse shopView = service.shopDetail(shopId, id);
        assertNull(shopView.getBuyer().getPhone(), "phone hidden from the shop before ACCEPTED");
        assertEquals(List.of("SEND_OFFER", "ACCEPT", "REJECT"), shopView.getAllowedActions());

        e = service.shopOffer(shopId, id, offer("9500"));
        assertEquals("QUOTED", e.getStatus());
        assertEquals("SHOP", e.getLastOfferBy());

        e = service.customerOffer(buyerA, id, offer("9000"));
        assertEquals("COUNTERED", e.getStatus());
        assertEquals(0, new BigDecimal("9000").compareTo(e.getLastOfferAmount()));

        e = service.shopAccept(shopId, id);
        assertEquals("ACCEPTED", e.getStatus());
        assertEquals(0, new BigDecimal("9000").compareTo(e.getAgreedAmount()));
        assertEquals("9876543210", e.getBuyer().getPhone(), "phone visible to the shop once ACCEPTED");
        assertEquals(List.of("REJECT", "CONFIRM"), e.getAllowedActions());

        e = service.shopConfirm(shopId, id, "pickup");
        assertEquals("CONFIRMED", e.getStatus());
        assertEquals("PICKUP", e.getFulfilment());
        assertEquals(1, p.getQuantity());
        assertEquals(1, p.getSoldQuantity());
        assertEquals("ACTIVE", p.getStatus());
        assertEquals(List.of("ENQUIRY", "OFFER", "COUNTER", "ACCEPT", "CONFIRM"),
                e.getOffers().stream().map(EnquiryResponse.Offer::getAction).toList());
        assertTrue(e.getAllowedActions().isEmpty());
    }

    @Test
    void confirmingLastUnitSellsOutAndCancelsOtherOpenEnquiries() {
        MarketplaceProduct p = product(1, "PICKUP,SHIPPING");
        UUID a = ask(buyerA, p, "ENQUIRY", null).getId();
        UUID b = ask(buyerB, p, "ENQUIRY", null).getId();

        service.shopOffer(shopId, a, offer("9800"));
        assertEquals("ACCEPTED", service.customerAccept(buyerA, a).getStatus());
        EnquiryResponse confirmed = service.shopConfirm(shopId, a, "SHIPPING");

        assertEquals("CONFIRMED", confirmed.getStatus());
        assertEquals(0, p.getQuantity());
        assertEquals(1, p.getSoldQuantity());
        assertEquals("SOLD", p.getStatus());
        MarketplaceEnquiry other = enquiries.get(b);
        assertEquals("CANCELLED", other.getStatus());
        assertTrue(steps.stream().anyMatch(s -> s.getEnquiryId().equals(b)
                && "SYSTEM".equals(s.getAuthor()) && "CANCEL".equals(s.getAction())));
    }

    @Test
    void invalidTransitionsAre409() {
        MarketplaceProduct p = product(3, "PICKUP");
        UUID id = ask(buyerA, p, "ENQUIRY", null).getId();

        assertThrows(ConflictException.class, () -> service.customerAccept(buyerA, id));   // no shop offer yet
        assertThrows(ConflictException.class, () -> service.shopAccept(shopId, id));       // no buyer offer
        assertThrows(ConflictException.class, () -> service.shopConfirm(shopId, id, "PICKUP")); // not ACCEPTED

        service.customerOffer(buyerA, id, offer("9000"));
        assertThrows(ConflictException.class, () -> service.customerOffer(buyerA, id, offer("8900"))); // own offer is latest

        service.shopOffer(shopId, id, offer("9600"));
        assertThrows(ConflictException.class, () -> service.shopAccept(shopId, id));       // shop's own quote is latest
        assertEquals("QUOTED", service.shopOffer(shopId, id, offer("9500")).getStatus()); // revising own quote is fine

        service.customerCancel(buyerA, id);
        assertThrows(ConflictException.class, () -> service.shopOffer(shopId, id, offer("9000")));
        assertThrows(ConflictException.class, () -> service.shopReject(shopId, id, "no"));
        assertThrows(ConflictException.class, () -> service.customerCancel(buyerA, id));
    }

    @Test
    void purchaseRequestWithoutOfferIsAcceptedAtListingPrice() {
        MarketplaceProduct p = product(1, "PICKUP");
        UUID id = ask(buyerA, p, "PURCHASE_REQUEST", null).getId();
        EnquiryResponse e = service.shopAccept(shopId, id);
        assertEquals("ACCEPTED", e.getStatus());
        assertEquals(0, p.getPrice().compareTo(e.getAgreedAmount()));
    }

    @Test
    void secondEnquiryOnSameListingReturnsTheOpenOne() {
        MarketplaceProduct p = product(1, "PICKUP");
        EnquiryService.CreateResult first = service.create(buyerA, null, p.getId(), new EnquiryCreateRequest());
        EnquiryService.CreateResult second = service.create(buyerA, null, p.getId(), new EnquiryCreateRequest());
        assertTrue(first.created());
        assertFalse(second.created());
        assertEquals(first.enquiry().getId(), second.enquiry().getId());
        assertEquals(1, enquiries.size());
    }

    @Test
    void confirmNeedsAFulfilmentTheListingOffers() {
        MarketplaceProduct p = product(1, "PICKUP");
        UUID id = ask(buyerA, p, "ENQUIRY", "9000").getId();
        service.shopAccept(shopId, id);
        assertThrows(BadRequestException.class, () -> service.shopConfirm(shopId, id, "SHIPPING"));
        assertThrows(BadRequestException.class, () -> service.shopConfirm(shopId, id, null));
        assertEquals(1, p.getQuantity());
    }

    @Test
    void ownershipAndValidation() {
        MarketplaceProduct p = product(1, "PICKUP");
        ResponseStatusException own = assertThrows(ResponseStatusException.class,
                () -> service.create(UUID.randomUUID(), shopId, p.getId(), new EnquiryCreateRequest()));
        assertEquals(HttpStatus.FORBIDDEN, own.getStatusCode());

        UUID id = ask(buyerA, p, "ENQUIRY", null).getId();
        assertThrows(ResourceNotFoundException.class, () -> service.shopDetail(UUID.randomUUID(), id));
        assertThrows(ResourceNotFoundException.class, () -> service.customerDetail(buyerB, id));
        assertThrows(BadRequestException.class, () -> service.shopOffer(shopId, id, offer("0")));
        assertThrows(BadRequestException.class,
                () -> service.create(buyerB, null, p.getId(), new EnquiryCreateRequest("OTHER", null, null, null)));

        p.setStatus("INACTIVE");
        assertThrows(ConflictException.class, () -> ask(buyerB, p, "ENQUIRY", null));
    }
}
