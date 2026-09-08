package com.greenhouse.backend.farm.application.inbound;

import com.greenhouse.backend.common.api.PageRequests;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.farm.domain.inbound.InboundStatus;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordResponse;
import com.greenhouse.backend.farm.repository.inbound.InboundRecordRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InboundRecordQueryService {

	private final InboundRecordRepository inboundRecordRepository;

	private final InboundRecordFinder inboundRecordFinder;

	public PageResponse<InboundRecordResponse> getInboundRecords(LocalDate from, LocalDate to, InboundType inboundType,
			InboundStatus status, String varietyKeyword, int page, int size) {
		PageRequests.validate(page, size);
		String keyword = normalize(varietyKeyword);
		return PageResponse.from(inboundRecordRepository
			.search(from, to, inboundType, status, keyword == null ? "" : keyword,
					PageRequest.of(page, size, Sort.by(Sort.Order.desc("inboundDate"), Sort.Order.desc("id"))))
			.map(InboundRecordResponse::from));
	}

	public InboundRecordResponse getInboundRecord(Long inboundRecordId) {
		return InboundRecordResponse.from(inboundRecordFinder.find(inboundRecordId));
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
