package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.auction.application.AuctionDataReader;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.dto.AuctionShipmentOptionResponse;
import com.greenhouse.backend.sales.dto.SalesSlipListItemResponse;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import com.greenhouse.backend.sales.repository.SalesSlipItemAllocationRepository;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SalesQueryService {
	private static final int LEGACY_LIST_LIMIT = 500;
	private static final int AUCTION_SHIPMENT_OPTION_LIMIT = 200;

	private final SalesSlipRepository salesSlipRepository;
	private final SalesSlipItemAllocationRepository allocationRepository;
	private final AuctionDataReader auctionDataReader;
	private final SalesSlipResponseAssembler responseAssembler;

	/**
	 * @deprecated Use {@link #getSalesSlipPage(Long, LocalDate, LocalDate, String, String, String, int, int)}.
	 */
	@Deprecated(since = "2026-08", forRemoval = false)
	public List<SalesSlipResponse> getSalesSlips(Long partnerId, LocalDate from, LocalDate to) {
		return assembleSalesSlips(salesSlipRepository.search(partnerId, from, to, LEGACY_LIST_LIMIT));
	}

	public PageResponse<SalesSlipListItemResponse> getSalesSlipPage(
			Long partnerId,
			LocalDate from,
			LocalDate to,
			String paymentStatus,
			String salesStatus,
			String keyword,
			int page,
			int size) {
		PageRequest pageable = PageRequest.of(
				Math.max(page, 0),
				Math.min(Math.max(size, 1), 100),
				Sort.by(Sort.Direction.DESC, "saleDate").and(Sort.by(Sort.Direction.DESC, "id")));
		String normalizedPaymentStatus = blankToNull(paymentStatus);
		String normalizedSalesStatus = blankToNull(salesStatus);
		String normalizedKeyword = blankToNull(keyword);
		var result = salesSlipRepository
				.searchPage(partnerId, from, to, normalizedPaymentStatus, normalizedSalesStatus, normalizedKeyword, pageable);
		return PageResponse.from(responseAssembler.assemblePage(result));
	}

	public SalesSlipResponse getSalesSlip(Long salesSlipId) {
		var salesSlip = salesSlipRepository.findWithDetailsById(salesSlipId)
				.orElseThrow(() -> new NotFoundException("판매 전표를 찾을 수 없습니다."));
		return assembleSalesSlips(List.of(salesSlip)).getFirst();
	}

	public List<AuctionShipmentOptionResponse> getAuctionShipmentOptions() {
		var availableIds = new ArrayList<Long>();
		for (int page = 0; availableIds.size() < AUCTION_SHIPMENT_OPTION_LIMIT; page++) {
			var candidates = auctionDataReader.getShipmentIdsNewestFirst(page, AUCTION_SHIPMENT_OPTION_LIMIT);
			if (candidates.isEmpty()) {
				break;
			}
			var usedIds = new HashSet<>(salesSlipRepository.findUsedAuctionShipmentIds(candidates));
			candidates.stream().filter(id -> !usedIds.contains(id))
					.limit(AUCTION_SHIPMENT_OPTION_LIMIT - availableIds.size()).forEach(availableIds::add);
			if (candidates.size() < AUCTION_SHIPMENT_OPTION_LIMIT) {
				break;
			}
		}
		return auctionDataReader.getShipmentsWithLotsNewestFirst(availableIds).stream()
				.map(AuctionShipmentOptionResponse::from)
				.toList();
	}

	private List<SalesSlipResponse> assembleSalesSlips(List<SalesSlip> salesSlips) {
		if (salesSlips.isEmpty()) {
			return List.of();
		}
		var salesSlipIds = salesSlips.stream().map(SalesSlip::getId).toList();
		Map<Long, List<SalesSlipItemAllocation>> allocationsByItemId = allocationRepository
				.findAllWithSnapshotsBySalesSlipIdIn(salesSlipIds)
				.stream()
				.collect(Collectors.groupingBy(
						allocation -> allocation.getSalesSlipItem().getId(),
						java.util.LinkedHashMap::new,
						Collectors.toList()));
		return responseAssembler.assemble(salesSlips, allocationsByItemId);
	}

	private String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
