package com.migia.OperationsHub.service;

import com.migia.OperationsHub.Repository.InventoryItemRepository;
import com.migia.OperationsHub.Repository.OrganizationRepository;
import com.migia.OperationsHub.Repository.ProductRepository;
import com.migia.OperationsHub.Service.InventoryService;
import com.migia.OperationsHub.model.InventoryItem;
import com.migia.OperationsHub.model.Organization;
import com.migia.OperationsHub.model.enums.OrganizationStatus;
import com.migia.OperationsHub.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class InventoryConcurrencyTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private UUID productId;

    @BeforeEach
    public void setup() {
        Organization org = Organization.builder()
                .name("Test Org")
                .slug("test-org")
                .status(OrganizationStatus.ACTIVE)
                .build();
        org = organizationRepository.save(org);

        Product product = Product.builder()
                .organization(org)
                .name("Test Product")
                .sku("TEST-SKU-1")
                .price(BigDecimal.TEN)
                .build();
        product = productRepository.save(product);
        productId = product.getId();

        InventoryItem item = InventoryItem.builder()
                .product(product)
                .quantityOnHand(1)
                .reservedQuantity(0)
                .build();
        inventoryItemRepository.save(item);
    }

    @Test
    public void testConcurrentStockAdjustment() throws InterruptedException {
        int numberOfThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.execute(() -> {
                try {
                    // Try to subtract 1 from stock
                    inventoryService.removeStock(productId, 1, "TEST_ADJUST", UUID.randomUUID().toString());
                    successCount.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException | com.migia.OperationsHub.exception.InsufficientStockException e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executorService.shutdown();

        // Exactly one should succeed, one should fail due to optimistic locking or negative stock check
        assertEquals(1, successCount.get(), "Only one thread should succeed");
        assertEquals(1, failureCount.get(), "One thread should fail");

        // Verify final state
        InventoryItem finalItem = inventoryItemRepository.findByProductId(productId).orElseThrow();
        assertEquals(0, finalItem.getQuantityOnHand(), "Quantity on hand should be 0");
    }
}
