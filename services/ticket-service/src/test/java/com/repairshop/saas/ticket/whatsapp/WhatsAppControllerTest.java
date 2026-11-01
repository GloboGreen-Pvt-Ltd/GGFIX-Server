package com.repairshop.saas.ticket.whatsapp;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Resend authorization. */
class WhatsAppControllerTest {

    private final WhatsAppNotificationService service = mock(WhatsAppNotificationService.class);
    private final WhatsAppController controller = new WhatsAppController(service);
    private final UUID shopId = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();

    private MockHttpServletRequest request(String... roles) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setAttribute("shopId", shopId.toString());
        r.setAttribute("roles", List.of(roles));
        return r;
    }

    @Test
    void technicianCannotResend() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.resend(ticketId, request("TECHNICIAN")));
        assertEquals(403, e.getStatusCode().value());
        verify(service, never()).resend(any(), any());
    }

    @Test
    void ownerCanResendWithinOwnShop() {
        controller.resend(ticketId, request("SHOP_OWNER"));
        verify(service).resend(shopId, ticketId);
    }
}
