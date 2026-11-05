package com.repairshop.saas.masterdata.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ModelRequest {
    private UUID brandId;
    private UUID categoryId;
    private UUID seriesId;
    private String name;
    /** Manufacturer model number(s), e.g. ["MZB0L8AIN","MZB0L88IN"]. */
    private List<String> modelNumber;
    private String slug;
    private String imageUrl;
    private String imageBase64;
    private String category;
    private Boolean sellActive;
    /** Inline colour names, e.g. ["Diamond Black","Skyline Blue"]. */
    private List<String> colors;
    /** Inline RAM+Storage labels, e.g. ["6 GB + 128 GB","8 GB + 128 GB"]. */
    private List<String> ramStorage;
}
