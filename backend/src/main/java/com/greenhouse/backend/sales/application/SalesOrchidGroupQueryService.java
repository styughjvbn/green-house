package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupReader;
import com.greenhouse.backend.sales.dto.SalesOrchidGroupSearchResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SalesOrchidGroupQueryService {

	private final OrchidGroupReader orchidGroupReader;

	public List<SalesOrchidGroupSearchResponse> search(String keyword, Long varietyId, String status) {
		String normalizedKeyword = keyword == null || keyword.isBlank() ? "" : keyword.trim();
		String normalizedStatus = status == null || status.isBlank() ? "" : status.trim();
		return orchidGroupReader.searchSellable(normalizedKeyword, varietyId, normalizedStatus).stream()
				.map(SalesOrchidGroupSearchResponse::from)
				.toList();
	}
}
