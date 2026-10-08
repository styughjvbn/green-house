package com.greenhouse.backend.sales.application.direct;

import com.greenhouse.backend.sales.application.document.DirectDocumentAccountingPort;
import com.greenhouse.backend.sales.domain.direct.DirectSale;
import com.greenhouse.backend.sales.domain.direct.DirectSalePrice;
import com.greenhouse.backend.sales.repository.direct.DirectSaleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class DirectSaleTermsWriter {
  private final DirectSaleRepository repository;
  private final DirectSaleReviewReader reviews;

  public void store(DirectDocumentAccountingPort.Terms terms) {
    var existing = repository.findForUpdate(terms.documentId());
    if (existing.isPresent()) reviews.requireClear(terms.documentId());
    var prices =
        terms.prices().stream()
            .map(
                price ->
                    new DirectSalePrice(
                        price.documentItemId(), price.quantity(), price.unitPrice()))
            .toList();
    DirectSale sale;
    if (existing.isPresent()) {
      // Lock the root first, then initialize its prices in one owner-side fetch query.
      sale = repository.findAllWithPrices(List.of(terms.documentId())).getFirst();
      sale.updateTerms(
          terms.partnerId(),
          terms.saleDate(),
          terms.expectedPaymentDate(),
          terms.paymentMethod(),
          prices);
    } else {
      sale =
          new DirectSale(
              terms.documentId(),
              terms.partnerId(),
              terms.saleDate(),
              terms.expectedPaymentDate(),
              terms.paymentMethod(),
              prices);
    }
    sale.changeUnpaidPaymentLabel(terms.unpaidPaymentLabel());
    repository.saveAndFlush(sale);
  }
}
