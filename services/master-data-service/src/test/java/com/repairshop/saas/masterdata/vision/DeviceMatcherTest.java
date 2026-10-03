package com.repairshop.saas.masterdata.vision;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DeviceMatcherTest {

    private static final UUID SAMSUNG = UUID.randomUUID();
    private static final UUID APPLE = UUID.randomUUID();
    private static final UUID MOBILE = UUID.randomUUID();
    private static final UUID TABLET = UUID.randomUUID();
    private static final Map<UUID, String> BRANDS = Map.of(SAMSUNG, "Samsung", APPLE, "Apple");

    private static DeviceMatcher.CatalogModel model(UUID brand, UUID cat, String name, String... numbers) {
        return new DeviceMatcher.CatalogModel(UUID.randomUUID(), brand, cat, null, name, List.of(numbers), null);
    }

    private static final DeviceMatcher.CatalogModel S8 = model(SAMSUNG, MOBILE, "Galaxy S8", "SM-G950FD", "SM-G950F");
    private static final DeviceMatcher.CatalogModel S8_PLUS = model(SAMSUNG, MOBILE, "Galaxy S8 Plus", "SM-G955FD", "SM-G955F");
    private static final DeviceMatcher.CatalogModel S8_ACTIVE = model(SAMSUNG, MOBILE, "Galaxy S8 Active", "SM-G892A");
    private static final DeviceMatcher.CatalogModel TAB_S8_PLUS = model(SAMSUNG, TABLET, "Galaxy Tab S8 Plus", "SM-X800");
    private static final DeviceMatcher.CatalogModel NOTE8 = model(SAMSUNG, MOBILE, "Galaxy Note 8", "SM-N950F");
    private static final DeviceMatcher.CatalogModel S9 = model(SAMSUNG, MOBILE, "Galaxy S9", "SM-G960F");
    private static final DeviceMatcher.CatalogModel S21_5G = model(SAMSUNG, MOBILE, "Galaxy S21 5G", "SM-G991B");
    private static final DeviceMatcher.CatalogModel IPHONE_11 = model(APPLE, MOBILE, "iPhone 11", "A2221");
    private static final DeviceMatcher.CatalogModel IPHONE_11_PRO = model(APPLE, MOBILE, "Apple iPhone 11 Pro", "A2215");
    private static final List<DeviceMatcher.CatalogModel> CATALOG =
            List.of(S8, S8_PLUS, S8_ACTIVE, TAB_S8_PLUS, NOTE8, S9, S21_5G, IPHONE_11, IPHONE_11_PRO);

    private static VisionSignals signals(List<String> guesses, Map<String, Double> entities, List<String> logos, String text) {
        List<VisionSignals.WebEntity> e = new ArrayList<>();
        entities.forEach((d, s) -> e.add(new VisionSignals.WebEntity(d, s)));
        return new VisionSignals(guesses, e, logos, text);
    }

    private static List<String> names(DeviceMatcher.Result r) {
        return r.matches().stream().map(m -> m.model().name()).toList();
    }

    @Test
    void googleSaysS8PlusRanksS8PlusFirstWithS8Next() {
        Map<String, Double> entities = new LinkedHashMap<>();
        entities.put("Samsung Galaxy S8+", 1.42);
        entities.put("Samsung Galaxy S8", 1.10);
        entities.put("Smartphone", 0.9);
        entities.put("Samsung", 0.8);
        entities.put("Samsung Galaxy", 0.7);
        DeviceMatcher.Result r = DeviceMatcher.match(CATALOG, BRANDS,
                signals(List.of("samsung galaxy s8+"), entities, List.of("Samsung"), "SAMSUNG\nDUOS"), 8);
        List<String> n = names(r);
        assertEquals("Galaxy S8 Plus", n.get(0), n.toString());
        assertTrue(n.indexOf("Galaxy S8") > 0, n.toString());
        assertFalse(n.contains("Galaxy Tab S8 Plus"), "a tablet is not a sibling of a phone: " + n);
        assertFalse(n.contains("iPhone 11"), n.toString());
        assertEquals(SAMSUNG, r.brandId());
        assertTrue(r.matches().get(0).score() >= 0.99);
    }

    @Test
    void googleSaysS8StillListsS8PlusAsAClosePick() {
        DeviceMatcher.Result r = DeviceMatcher.match(CATALOG, BRANDS,
                signals(List.of("samsung galaxy s8"), Map.of("Samsung Galaxy S8", 1.2, "Mobile Phones", 0.6), List.of(), ""), 8);
        List<String> n = names(r);
        assertEquals("Galaxy S8", n.get(0));
        assertTrue(n.contains("Galaxy S8 Plus"), "S8+ must stay one tap away: " + n);
        assertTrue(n.contains("Galaxy S8 Active"), n.toString());
        assertEquals("high", r.confidence());
        assertEquals("similar", r.matches().get(1).matchedBy());
    }

    @Test
    void printedModelNumberBeatsLabels() {
        DeviceMatcher.Result r = DeviceMatcher.match(CATALOG, BRANDS,
                signals(List.of("samsung galaxy s8"), Map.of("Samsung Galaxy S8", 1.0), List.of("Samsung"), "SM-G955FD\nCE0168"), 8);
        assertEquals("Galaxy S8 Plus", r.matches().get(0).model().name());
        assertEquals("modelNumber", r.matches().get(0).matchedBy());
    }

    @Test
    void optionalWordsInCatalogueNamesDoNotBlockAMatch() {
        DeviceMatcher.Result r = DeviceMatcher.match(CATALOG, BRANDS,
                signals(List.of("samsung galaxy s21"), Map.of(), List.of(), ""), 8);
        assertEquals("Galaxy S21 5G", r.matches().get(0).model().name());
    }

    @Test
    void brandInsideTheModelNameIsNotDoubleCounted() {
        DeviceMatcher.Result r = DeviceMatcher.match(CATALOG, BRANDS,
                signals(List.of("apple iphone 11 pro"), Map.of("iPhone 11 Pro", 1.0, "iPhone 11", 0.8), List.of("Apple"), ""), 8);
        assertEquals("Apple iPhone 11 Pro", r.matches().get(0).model().name());
        assertTrue(names(r).contains("iPhone 11"));
    }

    @Test
    void onlyTheLogoGivesTheBrandsModels() {
        DeviceMatcher.Result r = DeviceMatcher.match(CATALOG, BRANDS,
                signals(List.of("mobile phone"), Map.of("Smartphone", 1.0), List.of("Samsung"), ""), 20);
        assertEquals("low", r.confidence());
        assertEquals(SAMSUNG, r.brandId());
        assertTrue(r.matches().stream().allMatch(m -> m.model().brandId().equals(SAMSUNG)));
        assertTrue(r.matches().stream().allMatch(m -> m.matchedBy().equals("brand")));
    }

    @Test
    void nothingRecognisableGivesNothing() {
        DeviceMatcher.Result r = DeviceMatcher.match(CATALOG, BRANDS,
                signals(List.of("table"), Map.of("Furniture", 1.0), List.of(), ""), 8);
        assertTrue(r.matches().isEmpty());
        assertNull(r.brandId());
    }

    /** Google's actual answer for the catalogue's Galaxy S8 Plus product photo (3 Oct 2026). */
    @Test
    void realGoogleAnswerForAnS8PlusPhotoPutsS8PlusFirst() {
        Map<String, Double> entities = new LinkedHashMap<>();
        entities.put("Samsung Galaxy S8+ 64GB", 1.278);
        entities.put("Galaxy S8", 0.894);
        entities.put("Samsung", 0.723);
        entities.put("Samsung Electronics", 0.712);
        entities.put("Mobile Phone", 0.707);
        entities.put("Coral Blue", 0.704);
        entities.put("Arctic Silver", 0.704);
        entities.put("EDGE", 0.702);
        entities.put("Smartphone", 0.702);
        DeviceMatcher.Result r = DeviceMatcher.match(CATALOG, BRANDS,
                signals(List.of("le samsung galaxy s8"), entities, List.of("Samsung", "Samsung"), ""), 8);
        List<String> n = names(r);
        assertEquals("Galaxy S8 Plus", n.get(0), n.toString());
        assertEquals("Galaxy S8", n.get(1), n.toString());
        assertFalse(n.contains("Galaxy Tab S8 Plus"), n.toString());
        assertEquals("Samsung Galaxy S8+ 64GB", r.recognisedAs());
    }

    /** A real S8 photo: Google's top entity is the S8 itself, so no refinement to S8+. */
    @Test
    void weakerS8PlusEntityDoesNotOverrideAnS8Answer() {
        Map<String, Double> entities = new LinkedHashMap<>();
        entities.put("Samsung Galaxy S8", 1.3);
        entities.put("Samsung Galaxy S8+", 0.6);
        DeviceMatcher.Result r = DeviceMatcher.match(CATALOG, BRANDS,
                signals(List.of("samsung galaxy s8"), entities, List.of("Samsung"), ""), 8);
        assertEquals("Galaxy S8", r.matches().get(0).model().name());
        assertTrue(names(r).contains("Galaxy S8 Plus"));
    }

    /** Google's actual answer for a screenshot of the scanner: nothing but "smartphone". */
    @Test
    void realGoogleAnswerForAScreenshotFallsBackToTheBrandReadOffThePhone() {
        DeviceMatcher.Result r = DeviceMatcher.match(CATALOG, BRANDS,
                signals(List.of("smartphone"), Map.of("Smartphone", 0.407, "Screenshot", 0.338), List.of(),
                        "5:58\nVisual Device Scanner\nSAMSUNG\nDUOS\nDESIGNED & ENGINE\n1354359/0"), 8);
        assertEquals("low", r.confidence());
        assertEquals(SAMSUNG, r.brandId());
        assertTrue(r.matches().stream().allMatch(m -> "brand".equals(m.matchedBy())));
    }

    @Test
    void plusSignAndWordPlusTokeniseTheSame() {
        assertEquals(DeviceMatcher.tokens("Galaxy S8 Plus"), DeviceMatcher.tokens("Galaxy S8+").subList(0, 3));
        assertEquals(List.of("galaxy", "s8", "plus"), DeviceMatcher.tokens("Galaxy S8+"));
    }
}
