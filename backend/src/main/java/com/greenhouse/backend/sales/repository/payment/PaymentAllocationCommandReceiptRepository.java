package com.greenhouse.backend.sales.repository.payment;

import com.greenhouse.backend.sales.domain.payment.PaymentAllocationCommandReceipt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAllocationCommandReceiptRepository
    extends JpaRepository<PaymentAllocationCommandReceipt, Long> {
  Optional<PaymentAllocationCommandReceipt> findByPartnerIdAndRequestKey(
      Long partnerId, String requestKey);
}
