package com.greenhouse.backend.sales.document.api;

import java.util.List;
import java.util.Optional;

/** Common query projection only; Auction result lines remain the amount source. */
public interface AuctionDocumentAmountApi {
  Optional<Target> lockForLot(Long lotId);

  void updateGrossAmount(Long documentId, long grossAmount);

  record Target(Long documentId, List<Long> lotIds) {
    public Target {
      lotIds = List.copyOf(lotIds);
    }
  }
}
