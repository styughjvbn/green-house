package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.auction.application.AuctionDataReader;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.dto.AuctionShipmentOptionResponse;
import com.greenhouse.backend.sales.dto.SalesSlipListItemResponse;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import com.greenhouse.backend.sales.repository.SalesSlipItemAllocationRepository;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
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
		Page<SalesSlipListItemResponse> result = salesSlipRepository
				.searchPage(partnerId, from, to, normalizedPaymentStatus, normalizedSalesStatus, normalizedKeyword, pageable)
				.map(SalesSlipListItemResponse::from);
		return PageResponse.from(result);
	}

	public SalesSlipResponse getSalesSlip(Long salesSlipId) {
		var salesSlip = salesSlipRepository.findWithDetailsById(salesSlipId)
				.orElseThrow(() -> new NotFoundException("판매 전표를 찾을 수 없습니다."));
		return assembleSalesSlips(List.of(salesSlip)).getFirst();
	}

	public List<AuctionShipmentOptionResponse> getAuctionShipmentOptions() {
		var shipmentIds = salesSlipRepository.findAvailableAuctionShipmentIds(
				PageRequest.of(0, AUCTION_SHIPMENT_OPTION_LIMIT));
		return auctionDataReader.getShipmentsWithLotsNewestFirst(shipmentIds).stream()
				.map(AuctionShipmentOptionResponse::from)
				.toList();
	}

	private List<SalesSlipResponse> assembleSalesSlips(List<SalesSlip> salesSlips) {
		if (salesSlips.isEmpty()) {
			return List.of();
		}
		var salesSlipIds = salesSlips.stream().map(SalesSlip::getId).toList();
		Map<Long, List<SalesSlipItemAllocation>> allocationsByItemId = allocationRepository
				.findAllWithLocationBySalesSlipIdIn(salesSlipIds)
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
