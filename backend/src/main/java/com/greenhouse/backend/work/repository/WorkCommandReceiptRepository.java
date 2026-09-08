package com.greenhouse.backend.work.repository;

import com.greenhouse.backend.work.domain.operation.WorkCommandReceipt;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkCommandReceiptRepository extends JpaRepository<WorkCommandReceipt, String> {

	// The unique insert waits for an in-flight request; rollback releases its claim.
	@Modifying
	@Query(value = """
			INSERT INTO work_command_receipts (receipt_key, request_fingerprint, created_at)
			VALUES (:key, :fingerprint, :now) ON CONFLICT DO NOTHING
			""", nativeQuery = true)
	int claim(@Param("key") String key, @Param("fingerprint") String fingerprint, @Param("now") LocalDateTime now);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select receipt from WorkCommandReceipt receipt where receipt.receiptKey = :key")
	WorkCommandReceipt findForUpdate(@Param("key") String key);

}
