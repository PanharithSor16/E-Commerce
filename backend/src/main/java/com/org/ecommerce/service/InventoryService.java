package com.org.ecommerce.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.org.ecommerce.entity.Enums.InventoryReason;
import com.org.ecommerce.entity.InventoryTransaction;
import com.org.ecommerce.exception.ApiException;
import com.org.ecommerce.repository.InventoryTransactionRepository;
import com.org.ecommerce.repository.ProductVariantRepository;

/**
 * All stock movements go through here: a conditional UPDATE on the balance plus an append-only
 * ledger row, in the caller's transaction.
 */
@Service
public class InventoryService {

    private final ProductVariantRepository variants;
    private final InventoryTransactionRepository ledger;

    public InventoryService(ProductVariantRepository variants, InventoryTransactionRepository ledger) {
        this.variants = variants;
        this.ledger = ledger;
    }

    /** Removes stock for an order line; throws 409 if not enough stock is left. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Long variantId, int quantity, Long orderId, String sku) {
        if (variants.decrementStock(variantId, quantity) == 0) {
            throw ApiException.conflict("INSUFFICIENT_STOCK", "Not enough stock for " + sku + ".");
        }
        log(variantId, -quantity, InventoryReason.SALE, orderId, null, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void restore(Long variantId, int quantity, Long orderId, InventoryReason reason) {
        variants.incrementStock(variantId, quantity);
        log(variantId, quantity, reason, orderId, null, null);
    }

    /** Manual adjustment from the admin UI (restock, damage, correction). */
    @Transactional
    public int adjust(Long variantId, int change, InventoryReason reason, String note, Long userId) {
        if (change == 0) {
            throw ApiException.badRequest("INVALID_CHANGE", "Change must not be zero.");
        }
        if (reason == InventoryReason.SALE || reason == InventoryReason.ORDER_CANCELLED) {
            throw ApiException.badRequest("INVALID_REASON", "This reason is reserved for order processing.");
        }
        int updated = change > 0 ? variants.incrementStock(variantId, change) : variants.decrementStock(variantId, -change);
        if (updated == 0) {
            if (!variants.existsById(variantId)) {
                throw ApiException.notFound("Variant");
            }
            throw ApiException.conflict("INSUFFICIENT_STOCK", "Stock cannot go below zero.");
        }
        return log(variantId, change, reason, null, note, userId);
    }

    /** Records the starting stock of a newly created variant. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void initial(Long variantId, int quantity, Long userId) {
        if (quantity > 0) {
            log(variantId, quantity, InventoryReason.INITIAL, null, null, userId);
        }
    }

    private int log(Long variantId, int change, InventoryReason reason, Long orderId, String note, Long userId) {
        InventoryTransaction t = new InventoryTransaction();
        t.setVariantId(variantId);
        t.setChangeQuantity(change);
        t.setQuantityAfter(variants.currentStock(variantId));
        t.setReason(reason);
        t.setOrderId(orderId);
        t.setNote(note);
        t.setCreatedByUserId(userId);
        ledger.save(t);
        return t.getQuantityAfter();
    }
}
