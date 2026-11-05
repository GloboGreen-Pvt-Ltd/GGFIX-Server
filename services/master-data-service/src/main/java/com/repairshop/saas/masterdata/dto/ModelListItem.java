package com.repairshop.saas.masterdata.dto;

import com.repairshop.saas.masterdata.entity.MasterModel;

import java.util.List;
import java.util.UUID;

/**
 * One row of the admin Models table.
 *
 * <h2>Why a projection rather than the entity</h2>
 * The admin used to build its list by calling /brands/{id}/models once per brand —
 * 55 sequential round-trips — because returning everything at once had previously
 * exhausted the service's heap. Measured on the live catalogue, 82% of that payload
 * was base64: across 705 models, 1.56 MB of 1.90 MB came from just 19 rows still
 * holding a {@code data:} URI in image_url.
 *
 * So the weight is not the row count, it is a handful of legacy inline images. This
 * projection drops them, which turns ~3.76 MB across 55 requests into roughly 0.7 MB
 * in one. imageBase64 is never included either — it is unused by the table and
 * exists only on pre-S3 rows.
 *
 * Rows whose image is still inline report {@code inlineImage: true} with a null
 * imageUrl, so the table can show a placeholder and flag what needs re-uploading
 * rather than pretending the model has no image. The full value is still available
 * from GET /master/models/{id} when the edit form opens.
 *
 * @param imageUrl    the stored URL, or null when it is a {@code data:} URI
 * @param inlineImage true when the row still carries an inline data URI
 */
public record ModelListItem(
        UUID id,
        UUID brandId,
        UUID categoryId,
        UUID seriesId,
        String name,
        String slug,
        List<String> modelNumber,
        String category,
        Boolean sellActive,
        List<String> colors,
        List<String> ramStorage,
        String imageUrl,
        boolean inlineImage) {

    public static ModelListItem from(MasterModel m) {
        String url = m.getImageUrl();
        boolean inline = url != null && url.startsWith("data:");
        return new ModelListItem(
                m.getId(),
                m.getBrandId(),
                m.getCategoryId(),
                m.getSeriesId(),
                m.getName(),
                m.getSlug(),
                m.getModelNumber(),
                m.getCategory(),
                m.getSellActive(),
                m.getColors(),
                m.getRamStorage(),
                inline ? null : url,
                inline);
    }
}
