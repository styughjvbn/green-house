package com.greenhouse.backend.sales.application.direct;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.sales.domain.direct.DirectSaleAmounts;
import com.greenhouse.backend.sales.repository.direct.DirectSaleRepository;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class DirectSalePaymentPolicy {
  private final DirectSaleRepository repository;
  private final DirectSaleFinancialReader financials;

  public void lockTargets(Collection<Long> ids) {
    for (Long id : ids.stream().distinct().sorted().toList()) {
      repository
          .findForUpdate(id)
          .orElseThrow(
              () ->
                  new ConflictException(
                      "DIRECT_AMOUNT_SOURCE_MISSING", "일반 판매 금액 자료가 없어 배분을 처리할 수 없습니다."));
    }
  }

  public void requirePaymentAmount(Long documentId, Long expectedPartnerId, Long amount) {
    var sale =
        repository
            .findForUpdate(documentId)
            .orElseThrow(
                () ->
                    new ConflictException(
                        "DIRECT_AMOUNT_SOURCE_MISSING", "일반 판매 금액 자료가 없어 입금을 처리할 수 없습니다."));
    var financial = financials.findAll(List.of(documentId)).get(documentId);
    DirectSaleAmounts.requireReviewCleared(
        financial.reviewRequired() || !sale.getPartnerId().equals(expectedPartnerId));
    sale.requirePaymentAmount(financial.allocatedAmount().longValueExact(), amount);
  }
}
