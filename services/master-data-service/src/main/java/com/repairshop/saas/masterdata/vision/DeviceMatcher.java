package com.repairshop.saas.masterdata.vision;

import java.util.*;

/**
 * Turns Google Vision's description of a photo into ranked GGFIX catalogue
 * models. Pure — no I/O — so it is unit-tested on its own.
 *
 * <h2>How a label meets a model</h2>
 * Both sides are tokenised the same way ("+" reads as "plus", so Google's
 * "Samsung Galaxy S8+" and the catalogue's "Galaxy S8 Plus" agree). A model
 * matches a label when every word of the model's name (minus its brand) is in
 * the label; the score is how much of the label the model explains:
 * <pre>
 *   label "samsung galaxy s8+"   → Galaxy S8 Plus  3/3 = 1.00
 *                                → Galaxy S8       2/3 = 0.67
 *                                → Galaxy Tab S8+  ("tab" missing) — no match
 * </pre>
 * Google's best-guess label weighs 1.0; web entities weigh up to 0.9 by their
 * own relative score. A model number read off the photo ("SM-G955F") beats
 * every label. The top model's close siblings in the same category (S8 ↔ S8+,
 * S8 Active) are always listed after it, because a single photo of a phone's
 * back often can't tell them apart — the shop picks the right one.
 */
public final class DeviceMatcher {

    private DeviceMatcher() {}

    /** One catalogue model, reduced to what matching and the response need. */
    public record CatalogModel(UUID id, UUID brandId, UUID categoryId, UUID seriesId,
                               String name, List<String> modelNumbers, String imageUrl) {}

    /**
     * @param score     ranking score; ≥ 1.0 is an exact label or model-number match
     * @param matchedBy "modelNumber" | "label" | "similar" | "brand"
     */
    public record Match(CatalogModel model, double score, String matchedBy) {}

    /**
     * @param confidence "high" | "medium" | "low"
     * @param brandId    brand of the top match, or the brand Google saw when
     *                   nothing narrower matched; null when neither
     */
    public record Result(List<Match> matches, String confidence, UUID brandId) {}

    /** Words in web labels that say nothing about which model it is. */
    private static final Set<String> GENERIC = Set.of(
            "smartphone", "smartphones", "phone", "phones", "mobile", "telephone", "telephony", "cell",
            "device", "devices", "android", "ios", "gadget", "gadgets", "electronics", "electronic",
            "product", "communication", "communications", "portable", "feature", "case", "cover",
            "screen", "display", "series", "handset", "unlocked", "refurbished", "new", "used",
            "price", "review", "specs", "specifications", "the", "and", "for", "with", "of");

    /** Words a catalogue name may carry that a web label usually leaves out. */
    private static final Set<String> OPTIONAL = Set.of(
            "5g", "4g", "lte", "wifi", "duos", "dual", "sim", "edition");

    private static final double SIMILAR_FACTOR = 0.55;

    /**
     * Label matches below this are dropped: they come from a label explaining
     * only a sliver of the model ("iPhone 13" matching an unrelated "13 5G"
     * from another brand). Model-number hits are never dropped.
     */
    private static final double MIN_SCORE = 0.3;

    /** "Samsung Galaxy S8+" → [samsung, galaxy, s8, plus]. */
    public static List<String> tokens(String s) {
        if (s == null) return List.of();
        String t = s.toLowerCase(Locale.ROOT)
                .replace("+", " plus ")
                .replaceAll("wi-?fi", "wifi")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return t.isEmpty() ? List.of() : List.of(t.split(" "));
    }

    private static String normalizeCode(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static Result match(List<CatalogModel> catalog, Map<UUID, String> brandNames,
                               VisionSignals signals, int limit) {
        if (catalog == null || catalog.isEmpty() || signals == null) return new Result(List.of(), "low", null);

        // ── brands ──────────────────────────────────────────────────────────
        Map<UUID, List<String>> brandTokens = new HashMap<>();
        brandNames.forEach((id, name) -> {
            List<String> t = tokens(name);
            if (!t.isEmpty()) brandTokens.put(id, t);
        });
        Map<UUID, Double> seenBrands = new HashMap<>();
        signals.logos().forEach(logo -> mentionedBrands(tokens(logo), brandTokens)
                .forEach(b -> seenBrands.merge(b, 1.0, Math::max)));

        // ── labels: best guesses (1.0) + web entities (≤ 0.9) ───────────────
        List<Map.Entry<List<String>, Double>> phrases = new ArrayList<>();
        signals.bestGuessLabels().forEach(l -> phrases.add(Map.entry(tokens(l), 1.0)));
        double maxEntity = signals.webEntities().stream().mapToDouble(VisionSignals.WebEntity::score).max().orElse(0);
        for (VisionSignals.WebEntity e : signals.webEntities()) {
            if (e.description() == null || maxEntity <= 0) continue;
            phrases.add(Map.entry(tokens(e.description()), 0.9 * Math.min(1.0, e.score() / maxEntity)));
        }
        for (Map.Entry<List<String>, Double> p : phrases) {
            mentionedBrands(p.getKey(), brandTokens).forEach(b -> seenBrands.merge(b, p.getValue(), Math::max));
        }
        mentionedBrands(tokens(signals.text()), brandTokens).forEach(b -> seenBrands.merge(b, 0.6, Math::max));

        // Model cores: name words minus the model's own brand and OPTIONAL words.
        Map<UUID, Set<String>> cores = new HashMap<>();
        for (CatalogModel m : catalog) {
            Set<String> core = new LinkedHashSet<>(tokens(m.name()));
            core.removeAll(brandTokens.getOrDefault(m.brandId(), List.of()));
            core.removeAll(OPTIONAL);
            cores.put(m.id(), core);
        }

        Map<UUID, Double> best = new HashMap<>();
        Map<UUID, Integer> support = new HashMap<>();
        Map<UUID, String> how = new HashMap<>();

        for (Map.Entry<List<String>, Double> p : phrases) {
            List<String> pt = p.getKey();
            double weight = p.getValue();
            Set<UUID> mentioned = mentionedBrands(pt, brandTokens);
            Set<String> core = new LinkedHashSet<>(pt);
            core.removeAll(GENERIC);
            core.removeAll(OPTIONAL);
            mentioned.forEach(b -> core.removeAll(brandTokens.get(b)));
            if (core.isEmpty()) continue; // a bare brand / generic label

            for (CatalogModel m : catalog) {
                if (!mentioned.isEmpty() && !mentioned.contains(m.brandId())) continue;
                Set<String> mc = cores.get(m.id());
                if (mc.isEmpty() || !core.containsAll(mc)) continue;
                double specificity = (double) mc.size() / core.size();
                double s = weight * specificity;
                // A label without a brand ("Galaxy S8+") leans to the brand
                // Google saw elsewhere in the photo.
                if (mentioned.isEmpty() && !seenBrands.isEmpty() && !seenBrands.containsKey(m.brandId())) s *= 0.5;
                if (s > best.getOrDefault(m.id(), 0.0)) {
                    best.put(m.id(), s);
                    how.put(m.id(), "label");
                }
                if (specificity >= 0.999) support.merge(m.id(), 1, Integer::sum);
            }
        }

        // ── model numbers printed on the device / box ───────────────────────
        Map<String, List<CatalogModel>> byNumber = new HashMap<>();
        for (CatalogModel m : catalog) {
            for (String n : m.modelNumbers() == null ? List.<String>of() : m.modelNumbers()) {
                String key = normalizeCode(n);
                if (key.length() >= 4) byNumber.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
            }
        }
        for (String raw : signals.text().toUpperCase(Locale.ROOT).split("[^A-Z0-9-]+")) {
            String code = normalizeCode(raw);
            if (code.length() < 5 || !code.matches(".*\\d.*") || !code.matches(".*[a-z].*")) continue;
            List<CatalogModel> hits = byNumber.get(code);
            // A regional / carrier suffix the catalogue doesn't list.
            for (int len = code.length() - 1; hits == null && len >= 6; len--) hits = byNumber.get(code.substring(0, len));
            if (hits == null) continue;
            for (CatalogModel m : hits) {
                best.put(m.id(), 1.2);
                how.put(m.id(), "modelNumber");
            }
        }

        Map<UUID, CatalogModel> byId = new HashMap<>();
        catalog.forEach(m -> byId.put(m.id(), m));
        List<Match> ranked = new ArrayList<>();
        best.forEach((id, s) -> {
            if (s < MIN_SCORE && !"modelNumber".equals(how.get(id))) return;
            double boosted = s + 0.05 * Math.max(0, support.getOrDefault(id, 0) - 1);
            ranked.add(new Match(byId.get(id), Math.min(1.25, boosted), how.get(id)));
        });
        ranked.sort(Comparator.comparingDouble(Match::score).reversed()
                .thenComparing(m -> m.model().name(), String.CASE_INSENSITIVE_ORDER));

        // ── nothing narrower: the brand Google saw ──────────────────────────
        if (ranked.isEmpty()) {
            UUID brand = seenBrands.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
            if (brand == null) return new Result(List.of(), "low", null);
            List<Match> brandModels = catalog.stream()
                    .filter(m -> brand.equals(m.brandId()))
                    // Skip junk rows ("0") that would head an alphabetical list.
                    .filter(m -> m.name() != null && m.name().trim().length() >= 2 && m.name().matches(".*[A-Za-z].*"))
                    .sorted(Comparator.comparing(CatalogModel::name, String.CASE_INSENSITIVE_ORDER))
                    .limit(limit)
                    .map(m -> new Match(m, 0.0, "brand"))
                    .toList();
            return new Result(brandModels, "low", brand);
        }

        // ── the top model's close siblings (S8 ↔ S8+ ↔ S8 Active) ────────────
        Match top = ranked.get(0);
        if (top.score() >= 0.5) {
            Set<String> topCore = cores.get(top.model().id());
            Set<UUID> listed = new HashSet<>(best.keySet());
            List<Match> siblings = new ArrayList<>();
            for (CatalogModel m : catalog) {
                if (listed.contains(m.id())) continue;
                if (!Objects.equals(m.brandId(), top.model().brandId())
                        || !Objects.equals(m.categoryId(), top.model().categoryId())) continue;
                Set<String> mc = cores.get(m.id());
                if (mc.isEmpty()) continue;
                boolean superset = mc.containsAll(topCore) && mc.size() <= topCore.size() + 2;
                boolean subset = topCore.containsAll(mc) && topCore.size() - mc.size() <= 1 && mc.size() >= 2;
                if (superset || subset) siblings.add(new Match(m, top.score() * SIMILAR_FACTOR, "similar"));
            }
            siblings.sort(Comparator.comparing(x -> x.model().name(), String.CASE_INSENSITIVE_ORDER));
            ranked.addAll(siblings);
            ranked.sort(Comparator.comparingDouble(Match::score).reversed()
                    .thenComparing(m -> m.model().name(), String.CASE_INSENSITIVE_ORDER));
        }

        List<Match> out = ranked.size() > limit ? ranked.subList(0, limit) : ranked;
        double second = out.size() > 1 ? out.get(1).score() : 0;
        String confidence = top.score() >= 0.9 && second <= top.score() - 0.2 ? "high"
                : top.score() >= 0.5 ? "medium" : "low";
        return new Result(List.copyOf(out), confidence, top.model().brandId());
    }

    /** Brands whose name appears in the tokens as consecutive words. */
    private static Set<UUID> mentionedBrands(List<String> tokens, Map<UUID, List<String>> brandTokens) {
        Set<UUID> out = new HashSet<>();
        if (tokens.isEmpty()) return out;
        brandTokens.forEach((id, bt) -> {
            if (Collections.indexOfSubList(tokens, bt) >= 0) out.add(id);
        });
        return out;
    }
}
