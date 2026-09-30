package com.migia.OperationsHub.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getSalesByDate(UUID orgId) {
        String sql = "SELECT DATE(created_at) as date, SUM(totals) as total_sales " +
                     "FROM orders " +
                     "WHERE organization_id = ? AND status IN (2, 3) " + // PAID, COMPLETED
                     "GROUP BY DATE(created_at) " +
                     "ORDER BY date DESC";
        return jdbcTemplate.queryForList(sql, orgId.toString());
    }

    public List<Map<String, Object>> getOrderCountsByStatus(UUID orgId) {
        String sql = "SELECT status, COUNT(*) as count " +
                     "FROM orders " +
                     "WHERE organization_id = ? " +
                     "GROUP BY status";
        return jdbcTemplate.queryForList(sql, orgId.toString());
    }

    public List<Map<String, Object>> getTopProducts(UUID orgId, int limit) {
        String sql = "SELECT p.name, p.sku, SUM(oi.quantity) as total_sold " +
                     "FROM order_items oi " +
                     "JOIN orders o ON oi.order_id = o.id " +
                     "JOIN products p ON oi.product_id = p.id " +
                     "WHERE o.organization_id = ? AND o.status IN (2, 3) " + // PAID, COMPLETED
                     "GROUP BY p.id, p.name, p.sku " +
                     "ORDER BY total_sold DESC " +
                     "LIMIT ?";
        return jdbcTemplate.queryForList(sql, orgId.toString(), limit);
    }

    public List<Map<String, Object>> getLowStockItems(UUID orgId) {
        String sql = "SELECT p.name, p.sku, i.quantity_on_hand, i.low_stock_threshold " +
                     "FROM inventory_items i " +
                     "JOIN products p ON i.product_id = p.id " +
                     "WHERE p.organization_id = ? AND i.quantity_on_hand <= i.low_stock_threshold";
        return jdbcTemplate.queryForList(sql, orgId.toString());
    }

    public List<Map<String, Object>> getPaymentSuccessFailure(UUID orgId) {
        String sql = "SELECT status, COUNT(*) as count " +
                     "FROM payment " +
                     "WHERE organization_id = ? " +
                     "GROUP BY status";
        return jdbcTemplate.queryForList(sql, orgId.toString());
    }
}
