package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.auction.application.AuctionDataReader;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.dto.AuctionShipmentOptionResponse;
import com.greenhouse.backend.sales.dto.SalesSlipListItemResponse;
import com.greenhouse.backend.sales.dto.SalesSlipPageResponse;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SalesQueryService {
	private final SalesSlipRepository salesSlipRepository;
	private final AuctionDataReader auctionDataReader;

	public List<SalesSlipResponse> getSalesSlips(Long partnerId, LocalDate from, LocalDate to) {
		return salesSlipRepository.search(partnerId, from, to).stream()
				.map(SalesSlipResponse::from)
				.toList();
	}

	public SalesSlipPageResponse getSalesSlipPage(
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
		Page<SalesSlipListItemResponse> result = normalizedKeyword == null
				? salesSlipRepository
						.searchPage(partnerId, from, to, normalizedPaymentStatus, normalizedSalesStatus, pageable)
						.map(SalesSlipListItemResponse::from)
				: salesSlipRepository
						.searchPageWithKeyword(
								partnerId,
								from,
								to,
								normalizedPaymentStatus,
								normalizedSalesStatus,
								"%" + normalizedKeyword.toLowerCase() + "%",
								pageable)
						.map(SalesSlipListItemResponse::from);
		return SalesSlipPageResponse.from(result);
	}

	public SalesSlipResponse getSalesSlip(Long salesSlipId) {
		return salesSlipRepository.findWithDetailsById(salesSlipId)
				.map(SalesSlipResponse::from)
				.orElseThrow(() -> new NotFoundException("판매 전표를 찾을 수 없습니다."));
	}

	public List<AuctionShipmentOptionResponse> getAuctionShipmentOptions() {
		return auctionDataReader.getShipmentsNewestFirst().stream()
				.filter(shipment -> !salesSlipRepository.existsByAuctionShipmentId(shipment.getId()))
				.map(AuctionShipmentOptionResponse::from)
				.toList();
	}

	private String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
