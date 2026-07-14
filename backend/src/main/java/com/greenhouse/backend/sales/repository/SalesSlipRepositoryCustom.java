package com.greenhouse.backend.sales.repository;

import com.greenhouse.backend.sales.domain.SalesSlip;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SalesSlipRepositoryCustom {

	List<SalesSlip> search(Long partnerId, LocalDate from, LocalDate to);

	Page<SalesSlip> searchPage(
			Long partnerId,
			LocalDate from,
			LocalDate to,
			String paymentStatus,
			String salesStatus,
			String keyword,
			Pageable pageable);
}
