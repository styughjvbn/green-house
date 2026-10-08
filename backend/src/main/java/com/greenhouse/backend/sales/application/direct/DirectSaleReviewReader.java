package com.greenhouse.backend.sales.application.direct;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.sales.application.document.DirectDocumentAccountingPort.FinancialSnapshot;
import com.greenhouse.backend.sales.domain.direct.DirectSaleAmounts;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DirectSaleReviewReader {
  private final DirectSaleFinancialReader financials;

  public FinancialSnapshot requireClear(Long id) {
    return requireClear(id, null);
  }

  public FinancialSnapshot requireClear(Long id, Long expectedPartnerId) {
    var financial = financials.findAll(List.of(id)).get(id);
    if (financial == null)
      throw new ConflictException("DIRECT_AMOUNT_SOURCE_MISSING", "일반 판매 금액 자료를 찾을 수 없습니다.");
    DirectSaleAmounts.requireReviewCleared(
        financial.reviewRequired()
            || (expectedPartnerId != null && !expectedPartnerId.equals(financial.partnerId())));
    return financial;
  }
}
