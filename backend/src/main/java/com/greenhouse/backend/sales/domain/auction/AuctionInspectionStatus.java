package com.greenhouse.backend.sales.domain.auction;

import java.util.List;

public enum AuctionInspectionStatus {
  NORMAL,
  AUTO_MATCHED,
  CORRECTED_MATCH,
  MANUAL_REVIEW,
  MATCH_FAILED,
  QUANTITY_MISMATCH,
  RETURN_INFERRED,
  SOURCE_ERROR;

  public static List<AuctionInspectionStatus> reviewStatuses() {
    return List.of(MANUAL_REVIEW, MATCH_FAILED, QUANTITY_MISMATCH, RETURN_INFERRED, SOURCE_ERROR);
  }
}
