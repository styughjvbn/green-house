package com.greenhouse.backend.auction.application;

import com.greenhouse.backend.auction.domain.AuctionAttempt;
import com.greenhouse.backend.auction.domain.AuctionLotSearchCriteria;
import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.greenhouse.backend.auction.dto.AuctionLotAdjustmentRequest;
import com.greenhouse.backend.auction.dto.AuctionLotResponse;
import com.greenhouse.backend.auction.dto.AuctionLotReturnRequest;
import com.greenhouse.backend.auction.dto.AuctionLotStatusRequest;
import com.greenhouse.backend.auction.dto.AuctionTrackingSummaryResponse;
import com.greenhouse.backend.auction.repository.AuctionAttemptRepository;
import com.greenhouse.backend.auction.repository.AuctionLotStatusHistoryRepository;
import com.greenhouse.backend.auction.repository.AuctionShipmentLotRepository;
import com.greenhouse.backend.common.api.PageRequests;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.partner.domain.PartnerTextMatch;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuctionTrackingService {

	private final Clock clock;

	private final BusinessPartnerReader partnerReader;

	private final AuctionShipmentLotRepository lotRepository;

	private final AuctionAttemptRepository attemptRepository;

	private final AuctionLotStatusHistoryRepository statusHistoryRepository;

	private final RequestActorProvider requestActorProvider;

	public PageResponse<AuctionLotResponse> getLots(LocalDate from, LocalDate to, String market, String variety,
			String grade, AuctionLotStatus status, Boolean reviewOnly, Boolean returnOnly, Boolean waitingOnly,
			String keyword, int page, int size) {
		PageRequests.validate(page, size);
		var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
		String searchText = normalizeOrEmpty(keyword).toLowerCase();
		var boundaryMarkets = new LinkedHashMap<String, List<Long>>();
		for (int i = 0; i < searchText.length(); i++) {
			if (searchText.charAt(i) == ' ') {
				boundaryMarkets.put(searchText.substring(0, i),
						partnerReader.findMatchingIds(PartnerTextMatch.NAME_PREFIX, searchText.substring(i + 1)));
			}
		}
		var criteria = new AuctionLotSearchCriteria(from, to,
				blank(market) ? null : partnerReader.findMatchingIds(PartnerTextMatch.NAME_EXACT, market.trim()),
				normalizeOrEmpty(variety), normalizeOrEmpty(grade), status, Boolean.TRUE.equals(reviewOnly),
				Boolean.TRUE.equals(returnOnly), Boolean.TRUE.equals(waitingOnly), searchText, searchText.isEmpty()
						? List.of() : partnerReader.findMatchingIds(PartnerTextMatch.NAME_CONTAINS, searchText),
				boundaryMarkets);
		var result = lotRepository.search(criteria, pageable);
		var responses = assembleLots(result.getContent());
		return PageResponse.from(new PageImpl<>(responses, result.getPageable(), result.getTotalElements()));
	}

	public AuctionLotResponse getLot(Long id) {
		return assembleLots(List.of(findLot(id))).getFirst();
	}

	public AuctionTrackingSummaryResponse getSummary() {
		var summary = lotRepository.summarize();
		return new AuctionTrackingSummaryResponse(summary.getLotCount().intValue(),
				summary.getShippedQuantity().intValue(), summary.getSoldQuantity().intValue(),
				summary.getWaitingQuantity().intValue(), summary.getReturnedQuantity().intValue(),
				summary.getReviewRequiredCount().intValue(), summary.getTotalAmount().intValue());
	}

	@Transactional
	public AuctionLotResponse confirmReturn(Long id, AuctionLotReturnRequest request) {
		var lot = findLotForUpdate(id);
		lot.requireReturnConfirmable();
		int quantity = request.returnedQuantity() == null ? lot.getReturnConfirmableQuantity()
				: request.returnedQuantity();
		lot.confirmReturn(quantity, request.returnDate(), requestActorProvider.resolve(request.worker()),
				normalize(request.memo()), TimeConfig.utcNow(clock));
		return AuctionLotResponse.from(lot, partnerReader.getInfo(lot.getShipment().getAuctionHouseId()).name());
	}

	@Transactional
	public AuctionLotResponse adjust(Long id, AuctionLotAdjustmentRequest request) {
		var lot = findLotForUpdate(id);
		lot.adjustQuantities(request.soldQuantity(), request.waitingQuantity(), request.returnedQuantity(),
				requestActorProvider.resolve(request.worker()), normalize(request.memo()), TimeConfig.utcNow(clock));
		return AuctionLotResponse.from(lot, partnerReader.getInfo(lot.getShipment().getAuctionHouseId()).name());
	}

	@Transactional
	public AuctionLotResponse addResult(Long id, RecordAuctionResultCommand request) {
		var lot = findLotForUpdate(id);
		lot.recordResult(request.auctionDate(), request.attemptNo(), request.attemptStatus(), request.resultLines(),
				request.failedReason(), request.memo(), TimeConfig.utcNow(clock));

		return AuctionLotResponse.from(lot, partnerReader.getInfo(lot.getShipment().getAuctionHouseId()).name());
	}

	@Transactional
	public AuctionLotResponse changeStatus(Long id, AuctionLotStatusRequest request) {
		var lot = findLotForUpdate(id);
		lot.changeStatus(request.status(), request.reason().trim(), requestActorProvider.resolve(request.worker()),
				normalize(request.memo()), TimeConfig.utcNow(clock));
		return AuctionLotResponse.from(lot, partnerReader.getInfo(lot.getShipment().getAuctionHouseId()).name());
	}

	private AuctionShipmentLot findLot(Long id) {
		return lotRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("경매 출하 lot를 찾을 수 없습니다."));
	}

	private AuctionShipmentLot findLotForUpdate(Long id) {
		return lotRepository.findForUpdateById(id).orElseThrow(() -> new NotFoundException("경매 출하 lot를 찾을 수 없습니다."));
	}

	private List<AuctionLotResponse> assembleLots(List<AuctionShipmentLot> lots) {
		if (lots.isEmpty()) {
			return List.of();
		}
		var partners = partnerReader
			.getAllInfo(lots.stream().map(lot -> lot.getShipment().getAuctionHouseId()).toList());
		var lotIds = lots.stream().map(lot -> lot.getId()).toList();
		Map<Long, List<AuctionAttempt>> attemptsByLotId = attemptRepository.findAllWithResultLinesByLotIdIn(lotIds)
			.stream()
			.collect(Collectors.groupingBy(attempt -> attempt.getShipmentLot().getId(), LinkedHashMap::new,
					Collectors.toList()));
		var historiesByLotId = statusHistoryRepository.findAllByLotIdIn(lotIds)
			.stream()
			.collect(Collectors.groupingBy(history -> history.getShipmentLotId(), LinkedHashMap::new,
					Collectors.toList()));
		return lots.stream()
			.map(lot -> AuctionLotResponse.from(lot, partners.get(lot.getShipment().getAuctionHouseId()).name(),
					attemptsByLotId.getOrDefault(lot.getId(), List.of()),
					historiesByLotId.getOrDefault(lot.getId(), List.of())))
			.toList();
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private String normalize(String value) {
		return blank(value) ? null : value.trim();
	}

	private String normalizeOrEmpty(String value) {
		return blank(value) ? "" : value.trim();
	}

}
