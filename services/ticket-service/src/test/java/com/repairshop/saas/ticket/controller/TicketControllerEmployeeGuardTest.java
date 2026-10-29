package com.repairshop.saas.ticket.controller;

import com.repairshop.saas.ticket.dto.TicketResponse;
import com.repairshop.saas.ticket.security.CallerRoles;
import com.repairshop.saas.ticket.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Employee guards on the ticket endpoints. Owner tokens must reach the service
 * exactly as before; employee tokens go through the assigned-technician check.
 */
class TicketControllerEmployeeGuardTest {

    private final TicketService service = mock(TicketService.class);
    private final TicketController controller = new TicketController(service);
    private final UUID shopId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();

    private MockHttpServletRequest request(String... roles) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setAttribute("shopId", shopId.toString());
        r.setAttribute("userId", userId.toString());
        r.setAttribute("roles", List.of(roles));
        return r;
    }

    private static TicketController.ProgressEventRequest step(String key) {
        TicketController.ProgressEventRequest body = new TicketController.ProgressEventRequest();
        body.setStatusKey(key);
        return body;
    }

    @Test
    void employeeRolesAreRecognised() {
        assertTrue(CallerRoles.isEmployee(request("TECHNICIAN")));
        assertTrue(CallerRoles.isEmployee(request("STAFF")));
        assertTrue(CallerRoles.isEmployee(request("PICKUP_PERSON")));
        assertFalse(CallerRoles.isEmployee(request("SHOP_OWNER")));
        assertFalse(CallerRoles.isEmployee(request("SUPER_ADMIN")));
        assertFalse(CallerRoles.isEmployee(request("TECHNICIAN", "SHOP_OWNER")));
        assertFalse(CallerRoles.isEmployee(request("CUSTOMER")));
    }

    @Test
    void progressEventFromUnacceptedJobIsRefusedBeforeAnyWrite() {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Accept the job first."))
                .when(service).requireAssignedTechnician(shopId, userId, ticketId, true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.emitProgressEvent(ticketId, step("IN_REPAIR"), request("TECHNICIAN")));
        assertEquals(409, ex.getStatusCode().value());
        verify(service, never()).emitProgressStepEvent(any(), any(), any(), any(), any());
    }

    @Test
    void progressEventFromAcceptedAssignedTechnicianGoesThrough() {
        TicketController.ProgressEventRequest body = step("IN_REPAIR");
        body.setActor("OWNER");
        controller.emitProgressEvent(ticketId, body, request("TECHNICIAN"));
        verify(service).requireAssignedTechnician(shopId, userId, ticketId, true);
        verify(service).emitProgressStepEvent(shopId, ticketId, "IN_REPAIR", null, "TECHNICIAN");
    }

    @Test
    void ownerProgressEventSkipsTheEmployeeGuard() {
        controller.emitProgressEvent(ticketId, step("QUALITY_CHECK_COMPLETED"), request("SHOP_OWNER"));
        verify(service, never()).requireAssignedTechnician(any(), any(), any(), anyBoolean());
        verify(service).emitProgressStepEvent(shopId, ticketId, "QUALITY_CHECK_COMPLETED", null, null);
    }

    @Test
    void employeePatchOnlyCarriesTechnicianPhotos() {
        Map<String, Object> body = new HashMap<>();
        body.put("technicianPhotosJson", "[\"https://cdn/x.jpg\"]");
        body.put("assignedTechnicianId", UUID.randomUUID().toString());
        body.put("paymentAmount", 0);
        body.put("status", "DELIVERED");
        controller.patch(ticketId, body, request("TECHNICIAN"));
        verify(service).requireAssignedTechnician(shopId, userId, ticketId, true);
        verify(service).patch(shopId, ticketId, Map.of("technicianPhotosJson", "[\"https://cdn/x.jpg\"]"));
    }

    @Test
    void ownerPatchIsPassedThroughUntouched() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "READY");
        body.put("customerName", "Nandha K");
        controller.patch(ticketId, body, request("SHOP_OWNER"));
        verify(service).patch(shopId, ticketId, Map.of("status", "READY", "customerName", "Nandha K"));
    }

    @Test
    void employeesCannotUseOwnerOnlyTicketActions() {
        MockHttpServletRequest tech = request("TECHNICIAN");
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.updateStatus(ticketId, "CANCELLED", tech)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.reopen(ticketId, tech)).getStatusCode().value());
        verify(service, never()).updateStatus(any(), any(), anyString());
        verify(service, never()).reopen(any(), any());
    }

    @Test
    void employeeDetailIsRedactedButOwnerDetailIsNot() {
        when(service.getById(shopId, ticketId)).thenAnswer(inv -> TicketResponse.builder().customerPhone("8939615914").build());
        assertNull(controller.getById(ticketId, request("TECHNICIAN")).getCustomerPhone());
        assertEquals("8939615914", controller.getById(ticketId, request("SHOP_OWNER")).getCustomerPhone());
    }

    @Test
    void ownerCannotDeclineOnATechniciansBehalf() {
        TicketController.DeclineRequest body = new TicketController.DeclineRequest();
        body.setReason("Busy");
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.declineByTechnician(ticketId, body, request("SHOP_OWNER"))).getStatusCode().value());
        verifyNoInteractions(service);
        // The technician's own decline reaches the service with the reason trimmed.
        body.setReason("  Busy  ");
        controller.declineByTechnician(ticketId, body, request("TECHNICIAN"));
        verify(service).declineByTechnician(shopId, userId, ticketId, "Busy");
    }
}
