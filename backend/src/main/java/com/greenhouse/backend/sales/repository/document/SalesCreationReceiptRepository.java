package com.greenhouse.backend.sales.repository.document;

import com.greenhouse.backend.sales.domain.document.SalesCreationReceipt;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesCreationReceiptRepository
    extends JpaRepository<SalesCreationReceipt, String> {
  // The PK insert waits for an in-flight creation; a rollback releases the key.
  @Modifying
  @Query(
      value =
          """
      INSERT INTO sales_creation_receipts (request_key, request_fingerprint, created_at)
      VALUES (:key, :fingerprint, :now) ON CONFLICT DO NOTHING
      """,
      nativeQuery = true)
  int claim(
      @Param("key") String key,
      @Param("fingerprint") String fingerprint,
      @Param("now") LocalDateTime now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select receipt from SalesCreationReceipt receipt where receipt.requestKey = :key")
  SalesCreationReceipt findForUpdate(@Param("key") String key);
}
