package com.repairshop.saas.marketplace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repairshop.saas.marketplace.dto.BulkProductRequest;
import com.repairshop.saas.marketplace.dto.BulkResult;
import com.repairshop.saas.marketplace.dto.ProductRequest;
import com.repairshop.saas.marketplace.dto.ProductResponse;
import com.repairshop.saas.marketplace.dto.StatusChangeRequest;
import com.repairshop.saas.marketplace.entity.MarketplaceProduct;
import com.repairshop.saas.marketplace.exception.BadRequestException;
import com.repairshop.saas.marketplace.exception.ConflictException;
import com.repairshop.saas.marketplace.exception.ResourceNotFoundException;
import com.repairshop.saas.marketplace.repository.MarketplaceEnquiryRepository;
import com.repairshop.saas.marketplace.repository.MarketplaceProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** Listing status transitions and bulk idempotency, against an in-memory repository. */
class SellListingServiceTest {

    private final MarketplaceProductRepository productRepo = mock(MarketplaceProductRepository.class);
    private final MarketplaceEnquiryRepository enquiryRepo = mock(MarketplaceEnquiryRepository.class);
    private final EnquiryService enquiryService = mock(EnquiryService.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private final Map<UUID, MarketplaceProduct> rows = new LinkedHashMap<>();
    private final UUID shopId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private SellListingService service;

    @BeforeEach
    void setUp() {
        service = new SellListingService(productRepo, enquiryRepo, enquiryService, objectMapper, txManager);
        when(productRepo.saveAndFlush(any(MarketplaceProduct.class))).thenAnswer(inv -> store(inv.getArgument(0)));
        when(productRepo.save(any(MarketplaceProduct.class))).thenAnswer(inv -> store(inv.getArgument(0)));
        when(productRepo.findByIdForUpdate(any())).thenAnswer(inv -> Optional.ofNullable(rows.get(inv.<UUID>getArgument(0))));
        when(productRepo.findByIdAndShopId(any(), any())).thenAnswer(inv -> Optional.ofNullable(rows.get(inv.<UUID>getArgument(0)))
                .filter(p -> p.getShopId().equals(inv.getArgument(1))));
        when(productRepo.findByShopIdAndClientRef(any(), anyString())).thenAnswer(inv -> rows.values().stream()
                .filter(p -> p.getShopId().equals(inv.getArgument(0)) && inv.getArgument(1).equals(p.getClientRef()))
                .findFirst());
        when(productRepo.existsByShopIdAndImeiAndStatusIn(any(), anyString(), any())).thenAnswer(inv -> rows.values().stream()
                .anyMatch(p -> p.getShopId().equals(inv.getArgument(0)) && inv.getArgument(1).equals(p.getImei())
                        && inv.<Collection<String>>getArgument(2).contains(p.getStatus())));
    }

    private MarketplaceProduct store(MarketplaceProduct p) {
        if (p.getId() == null) p.setId(UUID.randomUUID());
        rows.put(p.getId(), p);
        return p;
    }

    private MarketplaceProduct listing(String status, int quantity, int images) {
        List<String> extras = new java.util.ArrayList<>();
        for (int i = 1; i < images; i++) extras.add("https://media.example/x" + i + ".jpg");
        return store(MarketplaceProduct.builder()
                .shopId(shopId).title("iPhone 13 128GB").type("SELL").status(status)
                .price(new BigDecimal("42000")).quantity(quantity).soldQuantity(0).negotiable(false)
                .listingType("DEVICE").fulfilmentOptions("PICKUP")
                .imageUrl(images > 0 ? "https://media.example/main.jpg" : null)
                .extraImageUrls(ProductMapper.serializeExtraImages(extras))
                .build());
    }

    private ProductResponse patch(MarketplaceProduct p, String status, Integer quantitySold) {
        return service.changeStatus(shopId, p.getId(), new StatusChangeRequest(status, quantitySold));
    }

    // ------------------------------------------------------------ transitions

    @Test
    void draftToActiveRequiresPublishableListing() {
        MarketplaceProduct p = listing("DRAFT", 1, 1); // device with a single photo
        BadRequestException ex = assertThrows(BadRequestException.class, () -> patch(p, "ACTIVE", null));
        assertTrue(ex.getMessage().contains("3 to 10 photos"), ex.getMessage());

        p.setExtraImageUrls("https://media.example/a.jpg,https://media.example/b.jpg");
        assertEquals("ACTIVE", patch(p, "ACTIVE", null).getStatus());
    }

    @Test
    void allowedTransitionsSucceedAndOthersAre409() {
        MarketplaceProduct p = listing("ACTIVE", 1, 3);
        assertEquals("INACTIVE", patch(p, "INACTIVE", null).getStatus());
        assertEquals("ACTIVE", patch(p, "ACTIVE", null).getStatus());
        assertEquals("DRAFT", patch(p, "DRAFT", null).getStatus());

        assertThrows(ConflictException.class, () -> patch(p, "SOLD", null));      // DRAFT -> SOLD
        assertThrows(ConflictException.class, () -> patch(p, "INACTIVE", null));  // DRAFT -> INACTIVE
        assertThrows(ConflictException.class, () -> patch(p, "DRAFT", null));     // DRAFT -> DRAFT
        assertThrows(ConflictException.class, () -> patch(p, "DELETED", null));
        assertThrows(BadRequestException.class, () -> patch(p, "BOGUS", null));

        MarketplaceProduct legacyCancelled = listing("CANCELLED", 1, 3);           // counts as INACTIVE
        assertEquals("ACTIVE", patch(legacyCancelled, "ACTIVE", null).getStatus());
    }

    @Test
    void partialSaleKeepsStatusAndFullSaleSellsOut() {
        MarketplaceProduct p = listing("ACTIVE", 3, 3);

        ProductResponse partial = patch(p, "SOLD", 2);
        assertEquals("ACTIVE", partial.getStatus());
        assertEquals(1, partial.getQuantity());
        assertEquals(2, partial.getSoldQuantity());
        verify(enquiryService, never()).cancelOpenForProduct(any(), any(), any());

        ProductResponse full = patch(p, "SOLD", null);
        assertEquals("SOLD", full.getStatus());
        assertEquals(0, full.getQuantity());
        assertEquals(3, full.getSoldQuantity());
        verify(enquiryService).cancelOpenForProduct(eq(p.getId()), isNull(), anyString());

        assertThrows(ConflictException.class, () -> patch(p, "ACTIVE", null));
        ProductRequest edit = new ProductRequest();
        edit.setPrice(new BigDecimal("1"));
        assertThrows(ConflictException.class, () -> service.update(shopId, p.getId(), edit));
    }

    @Test
    void anotherShopsListingIs404() {
        MarketplaceProduct p = listing("ACTIVE", 1, 3);
        UUID otherShop = UUID.randomUUID();
        assertThrows(ResourceNotFoundException.class,
                () -> service.changeStatus(otherShop, p.getId(), new StatusChangeRequest("INACTIVE", null)));
        assertThrows(ResourceNotFoundException.class, () -> service.get(otherShop, p.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(otherShop, p.getId()));
    }

    @Test
    void softDeleteHidesRowAndCancelsEnquiries() {
        MarketplaceProduct p = listing("ACTIVE", 1, 3);
        service.delete(shopId, p.getId());
        assertEquals("DELETED", p.getStatus());
        verify(enquiryService).cancelOpenForProduct(eq(p.getId()), isNull(), anyString());
        assertThrows(ResourceNotFoundException.class, () -> service.get(shopId, p.getId()));
    }

    @Test
    void activeUpdateMustStayPublishable() {
        MarketplaceProduct p = listing("ACTIVE", 1, 3);
        ProductRequest edit = new ProductRequest();
        edit.setQuantity(0);
        assertThrows(BadRequestException.class, () -> service.update(shopId, p.getId(), edit));
    }

    // -------------------------------------------------------------- create

    @Test
    void createWithSameClientRefReplaysExistingRow() {
        ProductRequest req = ProductRequest.builder()
                .title("Galaxy S21").price(new BigDecimal("25000")).listingType("SPARE_PART")
                .clientRef("ref-1").status("ACTIVE").build();

        SellListingService.CreateOutcome first = service.create(shopId, userId, req);
        SellListingService.CreateOutcome second = service.create(shopId, userId, req);

        assertFalse(first.replay());
        assertTrue(second.replay());
        assertEquals(first.product().getId(), second.product().getId());
        assertEquals(1, rows.size());
        verify(productRepo, times(1)).saveAndFlush(any(MarketplaceProduct.class));
    }

    // ---------------------------------------------------------------- bulk

    private JsonNode item(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    @Test
    void bulkSameClientRefTwiceIsExistingAndRetryNeverDuplicates() throws Exception {
        String sparePart = """
                {"clientRef":"c-1","title":"Battery","price":1500,"listingType":"SPARE_PART","quantity":4}""";
        BulkProductRequest body = new BulkProductRequest(UUID.randomUUID().toString(), true,
                List.of(item(sparePart), item(sparePart)));

        BulkResult first = service.bulk(shopId, userId, body);
        assertEquals("CREATED", first.getResults().get(0).getOutcome());
        assertEquals("EXISTING", first.getResults().get(1).getOutcome());
        assertEquals(first.getResults().get(0).getProductId(), first.getResults().get(1).getProductId());
        assertEquals(1, first.getCreated());
        assertEquals(1, first.getExisting());

        BulkResult retry = service.bulk(shopId, userId, body);
        assertEquals(0, retry.getCreated());
        assertEquals(2, retry.getExisting());
        assertEquals(1, rows.size());
        assertEquals(UUID.fromString(body.getBatchId()), rows.values().iterator().next().getBatchId());
    }

    @Test
    void bulkPublishSavesUnpublishableItemsAsDraftWithWarnings() throws Exception {
        BulkProductRequest body = new BulkProductRequest(null, true, List.of(
                item("""
                        {"clientRef":"d-1","title":"Pixel 7","price":30000,"listingType":"DEVICE","imageUrl":"https://m/1.jpg"}"""),
                item("""
                        {"clientRef":"d-2","title":"Charger","price":500,"listingType":"ACCESSORY"}""")));

        BulkResult r = service.bulk(shopId, userId, body);

        assertNotNull(r.getBatchId());
        BulkResult.ItemResult device = r.getResults().get(0);
        assertEquals("CREATED", device.getOutcome());
        assertEquals("DRAFT", device.getStatus());
        assertFalse(device.getWarnings().isEmpty());
        assertEquals("ACTIVE", r.getResults().get(1).getStatus());
        assertEquals(1, r.getPublished());
        assertEquals(1, r.getDrafts());
    }

    @Test
    void bulkBadItemsFailIndividually() throws Exception {
        listing("ACTIVE", 1, 3).setImei("356789012345678");
        BulkProductRequest body = new BulkProductRequest(UUID.randomUUID().toString(), false, List.of(
                item("""
                        {"clientRef":"b-1","title":"X","brandId":"ram:6 GB"}"""),
                item("""
                        {"clientRef":"b-2","price":100}"""),
                item("""
                        {"clientRef":"b-3","title":"Dup IMEI","imei":"356789012345678"}"""),
                item("""
                        {"clientRef":"b-4","title":"Good one","price":100}""")));

        BulkResult r = service.bulk(shopId, userId, body);

        assertEquals(List.of("FAILED", "FAILED", "FAILED", "CREATED"),
                r.getResults().stream().map(BulkResult.ItemResult::getOutcome).toList());
        assertEquals("brandId must be a valid UUID", r.getResults().get(0).getErrors().get(0));
        assertTrue(r.getResults().get(1).getErrors().contains("title is required"));
        assertTrue(r.getResults().get(2).getErrors().get(0).contains("IMEI"));
        assertEquals("DRAFT", r.getResults().get(3).getStatus()); // publish=false
        assertEquals(3, r.getFailed());
        assertEquals(1, r.getCreated());
    }

    @Test
    void bulkRejectsOversizedBatch() {
        List<JsonNode> items = new java.util.ArrayList<>();
        for (int i = 0; i < 101; i++) items.add(objectMapper.createObjectNode().put("title", "t" + i));
        assertThrows(BadRequestException.class,
                () -> service.bulk(shopId, userId, new BulkProductRequest(null, false, items)));
    }
}
