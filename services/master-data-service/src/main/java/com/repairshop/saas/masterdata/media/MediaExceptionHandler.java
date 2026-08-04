package com.repairshop.saas.masterdata.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns media failures into responses the admin UI can show verbatim.
 *
 * The status split is what matters: a caller that sent a 12 MB TIFF needs a 400 and
 * a message naming the problem, whereas an AccessDenied from S3 is a 502 — nothing
 * about the request was wrong and retrying it unchanged is reasonable. Collapsing
 * both into 500 is what makes "upload failed" tickets unanswerable.
 */
@RestControllerAdvice
public class MediaExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MediaExceptionHandler.class);

    @ExceptionHandler(MediaValidationException.class)
    public ResponseEntity<Map<String, Object>> onValidation(MediaValidationException e) {
        // Caller error: log at INFO, without a stack trace. These are routine.
        log.info("Media validation rejected: {}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * Spring aborts the request at the container level before any controller runs,
     * so this cannot be folded into the validator — without it the client gets an
     * opaque 500 with no indication that size was the problem.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> onTooLarge(MaxUploadSizeExceededException e) {
        log.info("Upload exceeded the servlet multipart limit: {}", e.getMessage());
        return build(HttpStatus.PAYLOAD_TOO_LARGE,
                "That file is too large. Please upload an image under 5 MB.");
    }

    @ExceptionHandler(MediaStorageException.class)
    public ResponseEntity<Map<String, Object>> onStorage(MediaStorageException e) {
        // Ours to fix: full stack trace, and a message that does not leak bucket
        // names or AWS detail to the browser.
        log.error("Media storage failure", e);
        return build(HttpStatus.BAD_GATEWAY,
                "Image storage is unavailable right now. The model was not saved — please try again.");
    }

    private static ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
