package com.greenhouse.backend.settlement.repository;

import com.greenhouse.backend.settlement.domain.PartnerBalanceSummary;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerBalanceSummaryRepository extends JpaRepository<PartnerBalanceSummary, Long> {
	@Query("""
			select b.partnerId as partnerId, b.receivableBalance as receivableBalance,
			       b.creditBalance as creditBalance, b.unappliedPaymentAmount as unappliedPaymentAmount
			from PartnerBalanceSummary b
			where b.receivableBalance <> 0 or b.creditBalance <> 0 or b.unappliedPaymentAmount <> 0
			""")
	List<Balance> findNonzeroBalances();

	interface Balance {
		Long getPartnerId();
		long getReceivableBalance();
		long getCreditBalance();
		long getUnappliedPaymentAmount();
	}

	Optional<PartnerBalanceSummary> findByPartnerId(Long partnerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select summary from PartnerBalanceSummary summary where summary.partnerId = :partnerId")
	Optional<PartnerBalanceSummary> findForUpdateByPartnerId(@Param("partnerId") Long partnerId);
}
