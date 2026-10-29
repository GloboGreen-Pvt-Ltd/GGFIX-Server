package com.repairshop.saas.ticket.service;

import com.repairshop.saas.ticket.dto.TicketResponse;
import com.repairshop.saas.ticket.entity.PlatformRepairBooking;
import com.repairshop.saas.ticket.entity.PlatformRepairBookingEvent;
import com.repairshop.saas.ticket.entity.Technician;
import com.repairshop.saas.ticket.entity.Ticket;
import com.repairshop.saas.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Employee (Staff app) job access: the pre-acceptance preview, the assigned-
 * technician guard and the Decline action.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketServiceEmployeeAccessTest {

    @Mock TicketRepository ticketRepository;
    @Mock com.repairshop.saas.ticket.repository.TechnicianRepository technicianRepository;
    @Mock com.repairshop.saas.ticket.repository.PlatformRepairBookingRepository platformRepairBookingRepository;
    @Mock com.repairshop.saas.ticket.repository.PlatformRepairBookingEventRepository platformRepairBookingEventRepository;
    @Mock com.repairshop.saas.ticket.repository.PlatformCustomerAddressRepository platformCustomerAddressRepository;
    @Mock com.repairshop.saas.ticket.repository.PlatformCustomerUserRepository platformCustomerUserRepository;
    @Mock com.repairshop.saas.ticket.repository.RepairNoteRepository repairNoteRepository;
    @Mock com.repairshop.saas.ticket.repository.TicketSolutionPackRepository ticketSolutionPackRepository;
    @Mock com.repairshop.saas.ticket.repository.MasterTechnicianWorkStatusViewRepository masterWorkStatusRepository;
    @Mock com.repairshop.saas.ticket.repository.InvoiceRepository invoiceRepository;
    @Mock CustomerOrderMirrorService customerOrderMirrorService;
    @Mock TicketNumberService ticketNumberService;

    @InjectMocks TicketService service;

    private final UUID shopId = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private Technician me;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        me = Technician.builder().id(UUID.randomUUID()).shopId(shopId).userId(userId).name("Ravi").build();
        ticket = new Ticket();
        ticket.setId(ticketId);
        ticket.setShopId(shopId);
        ticket.setStatus("IN_DIAGNOSIS");
        ticket.setAssignedTechnicianId(me.getId());
        when(ticketRepository.findByShopIdAndId(shopId, ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(technicianRepository.findByShopIdAndUserId(shopId, userId)).thenReturn(Optional.of(me));
        when(platformRepairBookingRepository.findByTicketId(ticketId))
                .thenReturn(Optional.of(PlatformRepairBooking.builder().id(bookingId).build()));
    }

    private static TicketResponse fullResponse(Instant acceptedAt) {
        return TicketResponse.builder()
                .customerId(UUID.randomUUID()).customerName("Nandha K").customerPhone("8939615914")
                .customerAddress("12 Main Rd").technicianAcceptedAt(acceptedAt).status("IN_DIAGNOSIS")
                .deviceDisplayName("Redmi Note 12").issueDescription("Display cracked")
                .estimatedPrice(new BigDecimal("1500")).finalPrice(new BigDecimal("1400"))
                .paymentType("ADVANCE").paymentAmount(new BigDecimal("500")).balanceAmount(new BigDecimal("900"))
                .paymentStatus("PAID").paymentPaidAt(Instant.now()).priceItemsJson("[{\"amount\":1500}]")
                .invoiceId(UUID.randomUUID()).invoiceNo("INV-1").invoiceGeneratedAt(Instant.now())
                .invoiceAmount(new BigDecimal("1400")).deviceSecurityType("PIN").deviceSecurityValue("1234")
                .build();
    }

    @Test
    void previewHidesCustomerContactMoneyAndUnlockCodeUntilAccepted() {
        TicketResponse r = TicketService.redactUntilAccepted(fullResponse(null));
        assertNull(r.getCustomerId());
        assertNull(r.getCustomerPhone());
        assertNull(r.getCustomerAddress());
        assertNull(r.getEstimatedPrice());
        assertNull(r.getFinalPrice());
        assertNull(r.getPaymentType());
        assertNull(r.getPaymentAmount());
        assertNull(r.getBalanceAmount());
        assertNull(r.getPaymentStatus());
        assertNull(r.getPaymentPaidAt());
        assertNull(r.getPriceItemsJson());
        assertNull(r.getInvoiceId());
        assertNull(r.getInvoiceNo());
        assertNull(r.getInvoiceGeneratedAt());
        assertNull(r.getInvoiceAmount());
        assertNull(r.getDeviceSecurityType());
        assertNull(r.getDeviceSecurityValue());
        // What the technician needs to decide on the job stays.
        assertEquals("Nandha K", r.getCustomerName());
        assertEquals("Redmi Note 12", r.getDeviceDisplayName());
        assertEquals("Display cracked", r.getIssueDescription());
        assertEquals("IN_DIAGNOSIS", r.getStatus());
    }

    @Test
    void acceptedJobIsNotRedacted() {
        TicketResponse r = TicketService.redactUntilAccepted(fullResponse(Instant.now()));
        assertEquals("8939615914", r.getCustomerPhone());
        assertEquals("1234", r.getDeviceSecurityValue());
        assertEquals(new BigDecimal("1500"), r.getEstimatedPrice());
        assertEquals("INV-1", r.getInvoiceNo());
    }

    @Test
    void guardRejectsJobAssignedToSomeoneElse() {
        ticket.setAssignedTechnicianId(UUID.randomUUID());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requireAssignedTechnician(shopId, userId, ticketId, false));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void guardRejectsTicketOutsideCallersShop() {
        UUID otherTicket = UUID.randomUUID();
        when(ticketRepository.findByShopIdAndId(shopId, otherTicket)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requireAssignedTechnician(shopId, userId, otherTicket, false));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void guardAllowsReadingOwnUnacceptedJobButWritesNeedAcceptance() {
        assertDoesNotThrow(() -> service.requireAssignedTechnician(shopId, userId, ticketId, false));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requireAssignedTechnician(shopId, userId, ticketId, true));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals("Accept the job first.", ex.getReason());

        ticket.setTechnicianAcceptedAt(Instant.now());
        assertDoesNotThrow(() -> service.requireAssignedTechnician(shopId, userId, ticketId, true));
    }

    @Test
    void declineUnassignsKeepsStatusAndRecordsReasonOnTimeline() {
        TicketResponse r = service.declineByTechnician(shopId, userId, ticketId, "Not trained on this model");

        assertNull(ticket.getAssignedTechnicianId());
        assertEquals("IN_DIAGNOSIS", ticket.getStatus());
        assertNull(r.getAssignedTechnicianId());
        ArgumentCaptor<PlatformRepairBookingEvent> event = ArgumentCaptor.forClass(PlatformRepairBookingEvent.class);
        verify(platformRepairBookingEventRepository).save(event.capture());
        assertEquals(bookingId, event.getValue().getBookingId());
        assertEquals("TECHNICIAN_DECLINED", event.getValue().getStatus());
        assertEquals("Not trained on this model", event.getValue().getNote());
        assertEquals("TECHNICIAN", event.getValue().getActor());
        verify(customerOrderMirrorService).emitShopNotificationForStatus(bookingId, "TECHNICIAN_DECLINED", "TECHNICIAN");
    }

    @Test
    void secondDeclineIsForbiddenNotAServerError() {
        service.declineByTechnician(shopId, userId, ticketId, "Busy");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.declineByTechnician(shopId, userId, ticketId, "Busy"));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void acceptedJobCannotBeDeclined() {
        ticket.setTechnicianAcceptedAt(Instant.now());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.declineByTechnician(shopId, userId, ticketId, "Changed my mind"));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals(me.getId(), ticket.getAssignedTechnicianId());
        verify(platformRepairBookingEventRepository, never()).save(any());
    }

    @Test
    void anotherTechnicianCannotDecline() {
        ticket.setAssignedTechnicianId(UUID.randomUUID());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.declineByTechnician(shopId, userId, ticketId, "Busy"));
        assertEquals(403, ex.getStatusCode().value());
        assertNotNull(ticket.getAssignedTechnicianId());
    }
}
