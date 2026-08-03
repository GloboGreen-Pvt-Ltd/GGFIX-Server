package com.repairshop.saas.masterdata.dto;

import lombok.Data;

@Data
public class FaqItemRequest {
    private String question;
    private String answer;
    private Integer sortOrder;
    private Boolean isActive;
}
