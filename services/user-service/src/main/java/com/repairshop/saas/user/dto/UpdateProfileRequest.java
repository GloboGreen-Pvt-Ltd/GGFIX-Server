package com.repairshop.saas.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    private String fullName;
    private String email;
    private String mobile;
    private String alternateMobile;
    private String profileImageUrl;
}
