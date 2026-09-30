package com.migia.OperationsHub.dto;

import com.migia.OperationsHub.model.Invoice;
import com.migia.OperationsHub.model.enums.InvoiceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class InvoiceResponse {
    private UUID id;
    private UUID orderId;
    private String number;
    private InvoiceStatus status;
    private BigDecimal total;
    private Instant createdAt;

    public static InvoiceResponse from(Invoice invoice) {
        return InvoiceResponse.builder()
                .id(invoice.getId())
                .orderId(invoice.getOrder().getId())
                .number(invoice.getNumber())
                .status(invoice.getStatus())
                .total(invoice.getTotal())
                .createdAt(invoice.getCreatedAt())
                .build();
    }
}
