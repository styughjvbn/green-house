package com.greenhouse.backend.sales.payment.repository;

import com.greenhouse.backend.sales.payment.domain.PaymentAllocationCommandReceipt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAllocationCommandReceiptRepository
    extends JpaRepository<PaymentAllocationCommandReceipt, Long> {
  Optional<PaymentAllocationCommandReceipt> findByPartnerIdAndRequestKey(
      Long partnerId, String requestKey);
}
