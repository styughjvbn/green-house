package com.greenhouse.backend.sales.document.application;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class SalesPhotoTransactionLifecycle {
  private final SalesPhotoCleanup cleanup;

  @Transactional(propagation = Propagation.MANDATORY)
  public AtomicBoolean track(String key) {
    var used = new AtomicBoolean();
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status == STATUS_ROLLED_BACK || (status == STATUS_COMMITTED && !used.get()))
              cleanup.discard(key);
          }
        });
    return used;
  }
}
