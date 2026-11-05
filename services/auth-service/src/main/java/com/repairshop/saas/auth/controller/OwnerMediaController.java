package com.repairshop.saas.auth.controller;

import com.repairshop.saas.auth.security.JwtService;
import com.repairshop.saas.auth.service.OwnerMediaService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Owner KYC and shop artwork uploads to media.ggfix.in.
 *
 * These only STORE the file and hand back its public URL. Saving that URL stays with
 * the existing {@code POST /auth/me/kyc-documents} and the shop update endpoints,
 * which already own the review-status transitions — writing the column here as well
 * would give two paths that can disagree about when KYC returns to PENDING_REVIEW.
 *
 * POST for both, including replacement: Tomcat only parses multipart bodies on POST
 * unless casual parsing is enabled, and silently receiving an empty file part is a
 * worse failure than an inexact verb.
 */
@RestController
@RequestMapping("/auth")
public class OwnerMediaController {

    private final OwnerMediaService media;
    private final JwtService jwtService;

    public OwnerMediaController(OwnerMediaService media, JwtService jwtService) {
        this.media = media;
        this.jwtService = jwtService;
    }

    /**
     * <pre>
     * POST /auth/me/kyc-documents/upload   (multipart/form-data)
     *   type   aadhaar-front | aadhaar-back | pan | avatar
     *   file   jpeg | png | webp | pdf   (avatar: image only)
     * </pre>
     *
     * Lands at {@code shopowner/{owner-name}/aadhaar-front-3f9c11ab.jpg}. Scoped to
     * the caller's own token — an owner can never write into another owner's folder,
     * because the folder is derived from the authenticated user, not from input.
     */
    @PostMapping(value = "/me/kyc-documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload one owner KYC document",
            description = "Stores the file in S3 and returns its public media.ggfix.in URL. "
                    + "Save that URL via POST /auth/me/kyc-documents.")
    public ResponseEntity<Map<String, String>> uploadOwnerDocument(HttpServletRequest request,
                                                                   @RequestParam("type") String type,
                                                                   @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(media.uploadOwnerDocument(requireUserId(request), type, file));
    }

    /**
     * <pre>
     * POST /auth/shops/{shopId}/media   (multipart/form-data)
     *   type   shop-front | shop-banner | gst | udyam
     *   file   jpeg | png | webp  (gst/udyam also accept pdf)
     * </pre>
     *
     * Lands at {@code shops/{shop-name}/gst-2b81f0aa.pdf}.
     */
    @PostMapping(value = "/shops/{shopId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload one shop image or certificate",
            description = "Stores the file in S3 and returns its public media.ggfix.in URL.")
    public ResponseEntity<Map<String, String>> uploadShopDocument(HttpServletRequest request,
                                                                  @PathVariable UUID shopId,
                                                                  @RequestParam("type") String type,
                                                                  @RequestParam("file") MultipartFile file) {
        // The service verifies this caller actually owns the shop — /auth/** is
        // permitAll in SecurityConfig, so the controller is the enforcement point.
        return ResponseEntity.ok(media.uploadShopDocument(requireUserId(request), shopId, type, file));
    }

    /**
     * Mirrors AuthController.requireUserId. Deriving the owner folder from the token
     * rather than from a request parameter is what stops one owner writing into
     * another's folder.
     */
    private UUID requireUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new com.repairshop.saas.auth.exception.UnauthorizedException(
                    "Missing or invalid Authorization header");
        }
        return jwtService.getUserId(header.substring("Bearer ".length()).trim());
    }
}
