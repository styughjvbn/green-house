package com.greenhouse.backend.sales.document.application;

import com.greenhouse.backend.sales.document.domain.SalesPhotoReference;
import com.greenhouse.backend.sales.document.repository.SalesSlipPhotoRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesPhotoMaintenanceTransactions {
  private final SalesSlipPhotoRepository photos;

  public List<SalesPhotoReference> referencesAfter(long after) {
    return photos.findReferencesAfter(after, PageRequest.of(0, 100));
  }

  public Set<String> referencedKeys(Collection<String> keys) {
    return keys.isEmpty() ? Set.of() : Set.copyOf(photos.findReferencedKeys(keys));
  }
}
