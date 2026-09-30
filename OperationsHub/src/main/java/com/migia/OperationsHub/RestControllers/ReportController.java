package com.migia.OperationsHub.RestControllers;

import com.migia.OperationsHub.Service.ReportService;
import com.migia.OperationsHub.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/sales-by-date")
    public ResponseEntity<List<Map<String, Object>>> getSalesByDate() {
        UUID orgId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(reportService.getSalesByDate(orgId));
    }

    @GetMapping("/order-counts")
    public ResponseEntity<List<Map<String, Object>>> getOrderCounts() {
        UUID orgId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(reportService.getOrderCountsByStatus(orgId));
    }

    @GetMapping("/top-products")
    public ResponseEntity<List<Map<String, Object>>> getTopProducts(@RequestParam(defaultValue = "10") int limit) {
        UUID orgId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(reportService.getTopProducts(orgId, limit));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<Map<String, Object>>> getLowStock() {
        UUID orgId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(reportService.getLowStockItems(orgId));
    }

    @GetMapping("/payment-stats")
    public ResponseEntity<List<Map<String, Object>>> getPaymentStats() {
        UUID orgId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(reportService.getPaymentSuccessFailure(orgId));
    }
}
