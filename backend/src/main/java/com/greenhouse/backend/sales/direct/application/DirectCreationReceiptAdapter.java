package com.greenhouse.backend.sales.direct.application;

import com.greenhouse.backend.sales.document.spi.DirectCreationReceiptPort;
import com.greenhouse.backend.sales.payment.api.ManualPaymentApi;
import com.greenhouse.backend.sales.payment.api.ManualPaymentCommand;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class DirectCreationReceiptAdapter implements DirectCreationReceiptPort {
  private final ManualPaymentApi payments;
  private final DirectPaymentAllocationTarget target;
  private final DirectSaleFinancialReader financials;

  public void collect(Long documentId, Long amount, LocalDate date, boolean cash) {
    long remaining =
        financials.findAll(List.of(documentId)).get(documentId).remainingAmount().longValueExact();
    payments.receiveAndAllocate(
        documentId,
        PaymentTargetType.SALES_SLIP,
        new ManualPaymentCommand(
            amount, date, "sales-create-" + documentId, cash ? "현금" : "계좌이체", null, null, null),
        Math.min(amount, remaining),
        target);
  }
}
