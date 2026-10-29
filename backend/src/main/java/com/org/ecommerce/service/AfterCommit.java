package com.org.ecommerce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs side effects (emails, analytics, independent log writes) only once the surrounding
 * transaction has committed.
 * <p>
 * Important for REQUIRES_NEW writes that reference rows created by the outer transaction: run
 * inline, they open a second connection whose foreign-key check blocks on the outer transaction's
 * uncommitted row while the outer transaction waits for them, an app-level deadlock SQL Server cannot
 * detect.
 */
public final class AfterCommit {

    private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        action.run();
                    } catch (RuntimeException e) {
                        log.warn("Post-commit action failed: {}", e.getMessage());
                    }
                }
            });
        } else {
            action.run();
        }
    }
}
