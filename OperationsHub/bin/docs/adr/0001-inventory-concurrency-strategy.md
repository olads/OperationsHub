# ADR 0001: Inventory Concurrency Strategy

## Status
Accepted

## Context
The OperationsHub platform handles high-volume inventory adjustments and order reservations.
Multiple concurrent requests might try to modify the stock of the same `InventoryItem` simultaneously. 
If not handled properly, this can lead to lost updates (one thread overwriting another's adjustment) or overselling (allowing inventory to drop below zero).

## Decision
We will use **Optimistic Locking** via JPA's `@Version` annotation on the `InventoryItem` entity to handle concurrent updates. 
We will also perform application-level invariant checks (e.g., preventing negative `quantityOnHand` or `reservedQuantity`) inside the entity's business methods (`adjustStock` and `reserveStock`).

## Rationale
1. **Performance**: Optimistic locking avoids the database-level lock overhead and contention associated with Pessimistic Locking (`PESSIMISTIC_WRITE`). It assumes conflicts are rare. If a conflict occurs, a `ObjectOptimisticLockingFailureException` is thrown.
2. **Safety**: The `@Version` field ensures that if two transactions read the same version of an inventory item and attempt to update it, the first one to commit will succeed (incrementing the version), and the second will fail since the version no longer matches. This guarantees no lost updates.
3. **Data Integrity**: By combining optimistic locking with application-level invariant checks inside the `InventoryItem` methods, we ensure that an item's stock cannot be reduced below zero even if multiple threads race to consume the final unit.

## Consequences
- The application (controllers or clients) must be prepared to handle `ObjectOptimisticLockingFailureException`. They can either present an error to the user ("Stock was updated by another user, please try again") or implement a retry mechanism for transient failures.
- High contention on a single hot item could result in many optimistic lock exceptions. If this becomes a bottleneck, we may need to explore event-driven asynchronous processing (e.g., CQRS/Event Sourcing) or pessimistic locking for specific hot paths.
