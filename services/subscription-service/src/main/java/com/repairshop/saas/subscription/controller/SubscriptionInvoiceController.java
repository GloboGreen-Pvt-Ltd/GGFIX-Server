package com.repairshop.saas.subscription.controller;

import com.repairshop.saas.subscription.dto.v2.V2Views.InvoiceView;
import com.repairshop.saas.subscription.invoice.InvoiceService;
import com.repairshop.saas.subscription.invoice.InvoiceService.PdfFile;
import com.repairshop.saas.subscription.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Subscription invoices (invoice contract §4). Owner JWT on every route except
 * the HMAC-signed public PDF link; platform admins may read any invoice.
 */
@RestController
@RequestMapping("/subscriptions/invoices")
@RequiredArgsConstructor
public class SubscriptionInvoiceController {

    private final CurrentUser currentUser;
    private final InvoiceService invoices;

    @GetMapping
    public List<InvoiceView> list() {
        return invoices.list(currentUser.requireOwnerId());
    }

    @GetMapping("/{id}")
    public InvoiceView get(@PathVariable UUID id) {
        CurrentUser.Actor actor = currentUser.requireUser();
        return invoices.get(id, actor.userId(), actor.isAdmin());
    }

    @GetMapping("/by-order/{orderId}")
    public InvoiceView byOrder(@PathVariable UUID orderId) {
        CurrentUser.Actor actor = currentUser.requireUser();
        return invoices.byOrder(orderId, actor.userId(), actor.isAdmin());
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        CurrentUser.Actor actor = currentUser.requireUser();
        return pdfResponse(invoices.pdf(id, actor.userId(), actor.isAdmin()), true);
    }

    @PostMapping("/{id}/whatsapp/retry")
    public InvoiceView retryWhatsApp(@PathVariable UUID id) {
        CurrentUser.Actor actor = currentUser.requireUser();
        return invoices.retryWhatsApp(id, actor.userId(), actor.isAdmin());
    }

    /** No JWT: BotBee fetches the WhatsApp document header from here. HMAC-signed, expires within 60 minutes. */
    @GetMapping("/public/{id}.pdf")
    public ResponseEntity<byte[]> publicPdf(@PathVariable UUID id, @RequestParam("exp") long exp,
                                            @RequestParam("sig") String sig) {
        return pdfResponse(invoices.publicPdf(id, exp, sig), false);
    }

    private static ResponseEntity<byte[]> pdfResponse(PdfFile file, boolean attachment) {
        ContentDisposition disposition = (attachment ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(file.fileName()).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .contentLength(file.bytes().length)
                .body(file.bytes());
    }
}
