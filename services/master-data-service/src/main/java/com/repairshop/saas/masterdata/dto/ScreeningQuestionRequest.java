package com.repairshop.saas.masterdata.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ScreeningQuestionRequest {
    private UUID deviceCategoryId;
    private String flow;
    private String question;
    private String helperText;
    private Integer sortOrder;
    private Boolean isActive;
}
