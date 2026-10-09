package com.greenhouse.backend.sales.document.spi;

import java.time.LocalDate;

/** Participates in the document creation transaction using the existing Payment ledger. */
public interface DirectCreationReceiptPort {
  void collect(Long documentId, Long amount, LocalDate date, boolean cash);
}
