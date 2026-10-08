package com.greenhouse.backend.sales.application.direct;

import com.greenhouse.backend.sales.document.spi.DirectDocumentAccountingPort;
import com.greenhouse.backend.sales.domain.direct.DirectSale;
import com.greenhouse.backend.sales.domain.direct.DirectSaleAmounts;
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

  public DirectDocumentAccountingPort.QuotedPrices quotePrices(
      List<DirectDocumentAccountingPort.Price> inputs) {
    var prices =
        inputs.stream()
            .map(
                price ->
                    new DirectDocumentAccountingPort.PriceSnapshot(
                        price.quantity(),
                        price.unitPrice(),
                        DirectSaleAmounts.price(price.quantity(), price.unitPrice())))
            .toList();
    return new DirectDocumentAccountingPort.QuotedPrices(
        DirectSaleAmounts.total(
            prices.stream().map(DirectDocumentAccountingPort.PriceSnapshot::amount).toList()),
        prices);
  }

  public void store(DirectDocumentAccountingPort.Terms terms) {
    var existing = repository.findForUpdate(terms.documentId());
    if (existing.isPresent()) {
      var financial = reviews.requireClear(terms.documentId());
      if (financial.allocatedAmount().signum() > 0)
        throw new IllegalArgumentException("입금 이력이 있는 전표는 수정할 수 없습니다.");
    }
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
