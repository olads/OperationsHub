package com.migia.OperationsHub.Service;

import com.migia.OperationsHub.Repository.InvoiceRepository;
import com.migia.OperationsHub.Repository.OrderRepository;
import com.migia.OperationsHub.dto.InvoiceResponse;
import com.migia.OperationsHub.exception.InvalidOrderStateException;
import com.migia.OperationsHub.exception.ResourceNotFoundException;
import com.migia.OperationsHub.model.Invoice;
import com.migia.OperationsHub.model.Order;
import com.migia.OperationsHub.model.enums.InvoiceStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages invoice lifecycle.
 *
 * <h3>Key invariants</h3>
 * <ul>
 *   <li>Invoice total is snapshotted from the order at creation — never re-derived.</li>
 *   <li>Invoice numbers are unique per organization.</li>
 *   <li>All records are tenant-scoped by organization_id.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;

    /** Simple in-memory sequence for invoice numbers. In production, use a DB sequence. */
    private static final AtomicLong INVOICE_SEQUENCE = new AtomicLong(System.currentTimeMillis() % 100000);

    // ──────────────────────────────────────────────────────────────
    // Create Invoice (called by async consumer)
    // ──────────────────────────────────────────────────────────────

    /**
     * Creates an invoice for an order. Idempotent — if an invoice already
     * exists for the order, the existing one is returned.
     *
     * The total is snapshotted from the order at creation time.
     * Future changes to the order's totals will NOT affect this invoice.
     */
    @Transactional
    public InvoiceResponse createInvoice(UUID orderId, UUID organizationId) {
        // Idempotent — don't create duplicate invoice
        if (invoiceRepository.existsByOrderId(orderId)) {
            Invoice existing = invoiceRepository.findByOrderId(orderId).orElseThrow();
            log.info("Invoice already exists for orderId={}, invoiceId={}", orderId, existing.getId());
            return InvoiceResponse.from(existing);
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Generate unique invoice number
        String invoiceNumber = "INV-" + INVOICE_SEQUENCE.incrementAndGet();

        Invoice invoice = Invoice.builder()
                .organization(order.getOrganization())
                .order(order)
                .number(invoiceNumber)
                .status(InvoiceStatus.DRAFT)
                .total(order.getTotals())  // snapshot — historical stability
                .build();

        invoiceRepository.save(invoice);
        log.info("Invoice created: invoiceId={}, number={}, orderId={}, total={}",
                invoice.getId(), invoiceNumber, orderId, invoice.getTotal());

        return InvoiceResponse.from(invoice);
    }

    // ──────────────────────────────────────────────────────────────
    // Status Transitions
    // ──────────────────────────────────────────────────────────────

    @Transactional
    public InvoiceResponse issueInvoice(UUID invoiceId) {
        Invoice invoice = loadInvoice(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new InvalidOrderStateException(
                    "Invoice can only be issued from DRAFT, current=" + invoice.getStatus());
        }
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoiceRepository.save(invoice);
        return InvoiceResponse.from(invoice);
    }

    @Transactional
    public InvoiceResponse markInvoicePaid(UUID invoiceId) {
        Invoice invoice = loadInvoice(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new InvalidOrderStateException(
                    "Invoice can only be marked paid from ISSUED, current=" + invoice.getStatus());
        }
        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);
        return InvoiceResponse.from(invoice);
    }

    @Transactional
    public InvoiceResponse voidInvoice(UUID invoiceId) {
        Invoice invoice = loadInvoice(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.DRAFT && invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new InvalidOrderStateException(
                    "Invoice can only be voided from DRAFT or ISSUED, current=" + invoice.getStatus());
        }
        invoice.setStatus(InvoiceStatus.VOID);
        invoiceRepository.save(invoice);
        return InvoiceResponse.from(invoice);
    }

    // ──────────────────────────────────────────────────────────────
    // Read
    // ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceByOrderId(UUID orderId) {
        Invoice invoice = invoiceRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found for orderId: " + orderId));
        return InvoiceResponse.from(invoice);
    }

    // ──────────────────────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────────────────────

    private Invoice loadInvoice(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));
    }
}
