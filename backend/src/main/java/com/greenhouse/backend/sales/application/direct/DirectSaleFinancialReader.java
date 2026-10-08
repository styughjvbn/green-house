package com.greenhouse.backend.sales.application.direct;

import com.greenhouse.backend.sales.application.document.DirectDocumentAccountingPort.FinancialSnapshot;
import com.greenhouse.backend.sales.application.document.DirectDocumentAccountingPort.PriceSnapshot;
import com.greenhouse.backend.sales.application.payment.PaymentAllocationReader;
import com.greenhouse.backend.sales.domain.direct.DirectSaleAmounts;
import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import com.greenhouse.backend.sales.repository.direct.DirectSaleAmountReconciliationRepository;
import com.greenhouse.backend.sales.repository.direct.DirectSaleRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DirectSaleFinancialReader {
  private final DirectSaleRepository sales;
  private final DirectSaleAmountReconciliationRepository reconciliations;
  private final PaymentAllocationReader allocations;

  public Map<Long, FinancialSnapshot> findAll(Collection<Long> documentIds) {
    List<Long> ids = documentIds.stream().distinct().sorted().toList();
    Map<Long, FinancialSnapshot> result = new LinkedHashMap<>();
    for (int offset = 0; offset < ids.size(); offset += 500) {
      var batch = ids.subList(offset, Math.min(offset + 500, ids.size()));
      var ownedSales = sales.findAllWithPrices(batch);
      Map<Long, Long> partners = new LinkedHashMap<>();
      ownedSales.forEach(sale -> partners.put(sale.getDocumentId(), sale.getPartnerId()));
      var amounts = allocations.findAll(PaymentTargetType.SALES_SLIP, partners);
      Set<Long> reviewed =
          reconciliations.findAll(batch).stream()
              .filter(review -> review.requiresReview())
              .map(review -> review.getDocumentId())
              .collect(Collectors.toSet());
      for (var sale : ownedSales) {
        var allocation = amounts.get(sale.getDocumentId());
        var prices = new LinkedHashMap<Long, PriceSnapshot>();
        long itemSum = 0;
        boolean invalidPrice = false;
        for (var price : sale.getPrices()) {
          itemSum = Math.addExact(itemSum, price.getAmount());
          invalidPrice |=
              price.getPricedQuantity() <= 0
                  || price.getUnitPrice() < 0
                  || price.getAmount() < 0
                  || price.getAmount().longValue()
                      != (long) price.getPricedQuantity() * price.getUnitPrice();
          prices.put(
              price.getDocumentItemId(),
              new PriceSnapshot(
                  price.getPricedQuantity(), price.getUnitPrice(), price.getAmount()));
        }
        BigDecimal total = BigDecimal.valueOf(sale.getTotalAmount());
        boolean review =
            reviewed.contains(sale.getDocumentId())
                || sale.getPartnerId() == null
                || allocation.reviewRequired()
                || invalidPrice
                || itemSum != sale.getTotalAmount()
                || sale.getTotalAmount() < 0
                || allocation.amount().compareTo(total.max(BigDecimal.ZERO)) > 0
                || (allocation.partnerId() != null
                    && !allocation.partnerId().equals(sale.getPartnerId()));
        result.put(
            sale.getDocumentId(),
            new FinancialSnapshot(
                sale.getDocumentId(),
                sale.getPartnerId(),
                sale.getTotalAmount(),
                sale.getExpectedPaymentDate(),
                sale.getPaymentMethod(),
                allocation.amount(),
                total.subtract(allocation.amount()).max(BigDecimal.ZERO),
                review,
                DirectSaleAmounts.paymentStatus(sale.getTotalAmount(), allocation.amount()),
                Map.copyOf(prices)));
      }
    }
    return Map.copyOf(result);
  }
}
