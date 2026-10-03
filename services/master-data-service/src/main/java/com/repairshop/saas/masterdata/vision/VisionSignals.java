package com.repairshop.saas.masterdata.vision;

import java.util.List;

/**
 * What Google Cloud Vision said about one photo, reduced to the parts the
 * device matcher uses.
 *
 * @param bestGuessLabels webDetection.bestGuessLabels[].label — Google's own
 *                        one-line guess, e.g. "samsung galaxy s8+"
 * @param webEntities     webDetection.webEntities — what the web pages carrying
 *                        matching / similar images are about, with Google's score
 * @param logos           logoAnnotations[].description, e.g. "Samsung"
 * @param text            textAnnotations[0].description — all text in the photo
 */
public record VisionSignals(
        List<String> bestGuessLabels,
        List<WebEntity> webEntities,
        List<String> logos,
        String text
) {
    public record WebEntity(String description, double score) {}

    public VisionSignals {
        bestGuessLabels = bestGuessLabels == null ? List.of() : List.copyOf(bestGuessLabels);
        webEntities = webEntities == null ? List.of() : List.copyOf(webEntities);
        logos = logos == null ? List.of() : List.copyOf(logos);
        text = text == null ? "" : text;
    }
}
