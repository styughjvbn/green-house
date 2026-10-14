package com.greenhouse.backend.farm.inbound.repository;

import com.greenhouse.backend.farm.inbound.domain.InboundCreationReceipt;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InboundCreationReceiptRepository
    extends JpaRepository<InboundCreationReceipt, String> {
  // Concurrent creation waits on the PK claim; rollback lets the next request acquire it.
  @Modifying
  @Query(
      value =
          """
      INSERT INTO inbound_creation_receipts (request_key, request_fingerprint, created_at)
      VALUES (:key, :fingerprint, :now) ON CONFLICT DO NOTHING
      """,
      nativeQuery = true)
  int claim(
      @Param("key") String key,
      @Param("fingerprint") String fingerprint,
      @Param("now") LocalDateTime now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select receipt from InboundCreationReceipt receipt where receipt.requestKey = :key")
  InboundCreationReceipt findForUpdate(@Param("key") String key);
}
