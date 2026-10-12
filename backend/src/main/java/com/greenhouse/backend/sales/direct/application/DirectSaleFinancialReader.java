package com.greenhouse.backend.sales.direct.application;

import com.greenhouse.backend.sales.direct.domain.DirectSaleAmounts;
import com.greenhouse.backend.sales.direct.domain.DirectSaleChangeLedger;
import com.greenhouse.backend.sales.direct.repository.DirectSaleChangeRepository;
import com.greenhouse.backend.sales.direct.repository.DirectSaleRepository;
import com.greenhouse.backend.sales.document.spi.DirectDocumentAccountingPort.FinancialSnapshot;
import com.greenhouse.backend.sales.document.spi.DirectDocumentAccountingPort.PriceSnapshot;
import com.greenhouse.backend.sales.payment.api.PaymentAllocationQueryApi;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DirectSaleFinancialReader {
  private final DirectSaleRepository sales;
  private final DirectSaleChangeRepository changes;
  private final PaymentAllocationQueryApi allocations;

  public Map<Long, FinancialSnapshot> findAll(Collection<Long> documentIds) {
    List<Long> ids = documentIds.stream().distinct().sorted().toList();
    Map<Long, FinancialSnapshot> result = new LinkedHashMap<>();
    for (int offset = 0; offset < ids.size(); offset += 500) {
      var batch = ids.subList(offset, Math.min(offset + 500, ids.size()));
      var ownedSales = sales.findAllWithPrices(batch);
      var changesByDocument =
          changes.findByDocumentIdInOrderByIdAsc(batch).stream()
              .collect(Collectors.groupingBy(change -> change.getDocumentId()));
      Map<Long, Long> partners = new LinkedHashMap<>();
      ownedSales.forEach(sale -> partners.put(sale.getDocumentId(), sale.getPartnerId()));
      var amounts = allocations.findAll(PaymentTargetType.SALES_SLIP, partners);
      for (var sale : ownedSales) {
        var allocation = amounts.get(sale.getDocumentId());
        var prices = new LinkedHashMap<Long, PriceSnapshot>();
        boolean invalidPrice = false;
        for (var price : sale.getPrices()) {
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
        var history = changesByDocument.getOrDefault(sale.getDocumentId(), List.of());
        var ledger = new DirectSaleChangeLedger(sale.getPrices(), history);
        BigDecimal total = BigDecimal.valueOf(sale.getTotalAmount());
        boolean review =
            sale.getPartnerId() == null
                || allocation.reviewRequired()
                || invalidPrice
                || ledger.total() != sale.getTotalAmount()
                || sale.getTotalAmount() < 0
                || allocation
                        .amount()
                        .compareTo(
                            BigDecimal.valueOf(ledger.allocationCeiling()).max(BigDecimal.ZERO))
                    > 0
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
                DirectSaleAmounts.paymentStatus(
                    sale.getTotalAmount(), allocation.amount(), sale.getUnpaidPaymentLabel()),
                DirectSaleAmounts.isPaymentAllowed(
                    sale.getTotalAmount(), allocation.amount(), review),
                !review && (sale.getTotalAmount() > 0 || allocation.amount().signum() > 0),
                Map.copyOf(prices),
                !history.isEmpty()));
      }
    }
    return Map.copyOf(result);
  }
}
