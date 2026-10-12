package com.greenhouse.backend.sales.document.api;

import java.time.LocalDate;
import java.util.List;

/** Document owns completion/ownership validation; Direct owns commercial changes. */
public interface DirectDocumentChangeApi {
  Target find(Long id);

  Target lock(Long id);

  record Item(Long id, String name, String spec) {}

  record Target(
      Long id, Long partnerId, LocalDate saleDate, boolean changeAllowed, List<Item> items) {}
}
