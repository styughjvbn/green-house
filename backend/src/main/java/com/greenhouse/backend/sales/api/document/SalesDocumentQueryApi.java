package com.greenhouse.backend.sales.api.document;

import com.greenhouse.backend.common.api.PageResponse;
import java.time.LocalDate;

public interface SalesDocumentQueryApi {
  PageResponse<SalesSlipSummary> getSalesSlipPage(
      Long partnerId,
      LocalDate from,
      LocalDate to,
      String paymentStatus,
      String salesStatus,
      String keyword,
      int page,
      int size);

  SalesSlipDocument getSalesSlip(Long salesSlipId);
}
