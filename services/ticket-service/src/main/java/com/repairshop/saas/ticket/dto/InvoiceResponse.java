package com.repairshop.saas.ticket.dto;

import com.repairshop.saas.ticket.entity.Invoice;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
public class InvoiceResponse {
    private UUID id;
    private UUID ticketId;
    private UUID shopId;

    private String invoiceNo;
    private Instant ticketDate;
    private Instant deliveryDate;
    private String gstNo;

    private BigDecimal serviceCharges;
    private BigDecimal totalRepairAmount;
    private BigDecimal spareUtilityCharge;
    private BigDecimal discount;
    private String taxMode;
    private BigDecimal gstPercent;

    private BigDecimal amount2Plus3;
    private BigDecimal baseAmount;
    private BigDecimal totalGst;
    private BigDecimal finalPayableAmount;
    private String amountInWords;

    private String spareLinesJson;
    private String serviceLinesJson;

    private Instant generatedAt;

    public static InvoiceResponse from(Invoice inv) {
        InvoiceResponse r = new InvoiceResponse();
        r.id = inv.getId();
        r.ticketId = inv.getTicketId();
        r.shopId = inv.getShopId();
        r.invoiceNo = inv.getInvoiceNo();
        r.ticketDate = inv.getTicketDate();
        r.deliveryDate = inv.getDeliveryDate();
        r.gstNo = inv.getGstNo();
        r.serviceCharges = inv.getServiceCharges();
        r.totalRepairAmount = inv.getTotalRepairAmount();
        r.spareUtilityCharge = inv.getSpareUtilityCharge();
        r.discount = inv.getDiscount();
        r.taxMode = inv.getTaxMode();
        r.gstPercent = inv.getGstPercent();
        r.amount2Plus3 = inv.getAmount2Plus3();
        r.baseAmount = inv.getBaseAmount();
        r.totalGst = inv.getTotalGst();
        r.finalPayableAmount = inv.getFinalPayableAmount();
        r.amountInWords = inv.getAmountInWords();
        r.spareLinesJson = inv.getSpareLinesJson();
        r.serviceLinesJson = inv.getServiceLinesJson();
        r.generatedAt = inv.getGeneratedAt();
        return r;
    }
}
