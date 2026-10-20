package com.greenhouse.backend.sales.document.application;

import com.greenhouse.backend.sales.document.application.port.out.SalesPhotoStorage;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalesPhotoCleanup {
  private final SalesPhotoStorage storage;

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void discard(String key) {
    try {
      storage.delete(key);
    } catch (RuntimeException e) {
      // Never turn a committed sale into a failed HTTP response for orphan cleanup.
      LoggerFactory.getLogger(getClass())
          .warn("sales_photo_cleanup_failed key={} type={}", key, e.getClass().getSimpleName());
    }
  }
}
