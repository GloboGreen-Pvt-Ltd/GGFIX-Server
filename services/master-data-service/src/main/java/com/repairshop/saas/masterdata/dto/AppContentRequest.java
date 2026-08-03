package com.repairshop.saas.masterdata.dto;

import lombok.Data;

@Data
public class AppContentRequest {
    private String code;
    private String title;
    private String body;
}
