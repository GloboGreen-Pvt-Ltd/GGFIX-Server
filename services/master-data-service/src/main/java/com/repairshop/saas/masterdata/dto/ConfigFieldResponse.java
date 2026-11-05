package com.repairshop.saas.masterdata.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigFieldResponse {
    private UUID id;
    private UUID deviceCategoryId;
    private String code;
    private String name;
    private Integer sortOrder;
    private Boolean isActive;
    private List<OptionDto> options;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OptionDto {
        private UUID id;
        private String value;
        private Integer sortOrder;
    }
}
