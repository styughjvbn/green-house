package com.greenhouse.backend.sales.document.application;

import com.greenhouse.backend.sales.document.application.port.out.SalesPhotoStorage;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class SalesPhotoMaintenanceService {
  private final SalesPhotoMaintenanceTransactions transactions;
  private final SalesPhotoStorage storage;
  private final Clock clock;

  public int verify() {
    outsideTransaction();
    int count = 0;
    long after = 0;
    while (true) {
      var batch = transactions.referencesAfter(after);
      if (batch.isEmpty()) return count;
      for (var photo : batch) {
        SalesPhotoIntegrity.verify(
            storage.read(photo.storageKey()), photo.byteSize(), photo.sha256());
        count++;
        after = photo.salesSlipId();
      }
    }
  }

  /** Offline only: writers must be stopped before applying deletions. */
  public int orphans(boolean delete) {
    outsideTransaction();
    int count = 0;
    String cursor = null;
    var cutoff = clock.instant().minus(Duration.ofHours(24));
    do {
      var page = storage.list(cursor, 500);
      var referenced =
          transactions.referencedKeys(
              page.objects().stream().map(SalesPhotoStorage.ObjectInfo::key).toList());
      for (var object : page.objects()) {
        if (object.modifiedAt().isBefore(cutoff) && !referenced.contains(object.key())) {
          count++;
          if (delete) storage.delete(object.key());
        }
      }
      cursor = page.nextCursor();
    } while (cursor != null);
    return count;
  }

  private static void outsideTransaction() {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Photo maintenance must run outside a database transaction");
  }
}
