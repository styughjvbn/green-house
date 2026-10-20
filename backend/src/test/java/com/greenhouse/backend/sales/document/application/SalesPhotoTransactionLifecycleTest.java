package com.greenhouse.backend.sales.document.application;

import static org.mockito.Mockito.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SalesPhotoTransactionLifecycleTest {
  @ParameterizedTest
  @CsvSource({
    "0,true,false",
    "0,false,true",
    "1,true,true",
    "1,false,true",
    "2,true,false",
    "2,false,false"
  })
  void retainsCommittedReferencesAndUnknownOutcomes(int status, boolean used, boolean deleted) {
    var cleanup = mock(SalesPhotoCleanup.class);
    TransactionSynchronizationManager.initSynchronization();
    try {
      var attached = new SalesPhotoTransactionLifecycle(cleanup).track("photo");
      attached.set(used);
      TransactionSynchronizationManager.getSynchronizations().getFirst().afterCompletion(status);
      if (deleted) verify(cleanup).discard("photo");
      else verifyNoInteractions(cleanup);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }
}
