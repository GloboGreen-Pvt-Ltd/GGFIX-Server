package com.repairshop.saas.masterdata.dto;

import lombok.Data;

/**
 * categoryType is a String here (not {@link com.repairshop.saas.masterdata.entity.CategoryMenuType}
 * directly) so a bad value from the client fails as a controller-level 400
 * rather than a Jackson deserialization 400 with a less specific message — the
 * controller parses it with {@code CategoryMenuType.valueOf(...)}.
 */
@Data
public class CategoryMenuRequest {
    private String categoryType;
    private String menuName;
    private String slug;
    private String description;
    private String imageUrl;
    private Integer sortOrder;
    private Boolean isActive;
}
