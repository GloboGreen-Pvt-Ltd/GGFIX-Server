package com.repairshop.saas.auth.controller;

import com.repairshop.saas.auth.dto.CustomerLocationView;
import com.repairshop.saas.auth.dto.ReverseGeocodeRequest;
import com.repairshop.saas.auth.dto.ReverseGeocodeResponse;
import com.repairshop.saas.auth.service.GeocodingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/location")
@RequiredArgsConstructor
@Tag(name = "Location", description = "Reverse geocoding for the customer location picker")
public class LocationController {

    private final GeocodingService geocodingService;

    @PostMapping("/reverse-geocode")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Reverse geocode",
            description = "Public endpoint. Resolves (lat,lng) to an address via Google Geocoding, server-side — "
                    + "the API key never reaches the client. Always responds 200; success=false on any failure "
                    + "(key not configured, no result, provider error) so the caller can fall back cleanly.")
    public ReverseGeocodeResponse reverseGeocode(@Valid @RequestBody ReverseGeocodeRequest request) {
        CustomerLocationView location = geocodingService.reverseGeocode(request.getLatitude(), request.getLongitude());
        if (location == null) {
            return ReverseGeocodeResponse.builder()
                    .success(false)
                    .message("Couldn't resolve an address for this location.")
                    .build();
        }
        return ReverseGeocodeResponse.builder()
                .success(true)
                .location(location)
                .build();
    }
}
