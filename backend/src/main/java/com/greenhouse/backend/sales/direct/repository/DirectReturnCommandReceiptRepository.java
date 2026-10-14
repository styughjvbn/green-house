package com.greenhouse.backend.sales.direct.repository;

import com.greenhouse.backend.sales.direct.domain.DirectReturnCommandReceipt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectReturnCommandReceiptRepository
    extends JpaRepository<DirectReturnCommandReceipt, Long> {
  Optional<DirectReturnCommandReceipt> findByDocumentIdAndRequestKey(Long documentId, String key);
}
