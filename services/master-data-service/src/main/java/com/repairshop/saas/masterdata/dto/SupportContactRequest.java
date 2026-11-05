package com.repairshop.saas.masterdata.dto;

import lombok.Data;

@Data
public class SupportContactRequest {
    private String label;
    private String email;
    private String phone;
    private String imageUrl;
    private Integer sortOrder;
    private Boolean isActive;
}
