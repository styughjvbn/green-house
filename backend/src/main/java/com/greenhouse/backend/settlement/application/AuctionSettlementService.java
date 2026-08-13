package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.auction.application.AuctionDataReader;
import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.settlement.domain.AuctionSettlement;
import com.greenhouse.backend.settlement.domain.AuctionSettlementStatus;
import com.greenhouse.backend.settlement.dto.AuctionSettlementResponse;
import com.greenhouse.backend.settlement.repository.AuctionSettlementRepository;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AuctionSettlementService {
	private final AuctionSettlementRepository settlementRepository;
	private final AuctionDataReader auctionDataReader;
	private final BusinessPartnerReader partnerReader;
	private final ExpectedPaymentDateCalculator paymentDateCalculator;

	@Transactional(readOnly = true)
	public List<AuctionSettlementResponse> getSettlements(
			Long auctionHouseId,
			LocalDate from,
			LocalDate to,
			AuctionSettlementStatus status) {
		return settlementRepository.search(auctionHouseId, from, to, status).stream()
				.map(AuctionSettlementResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public AuctionSettlementResponse getSettlement(Long settlementId) {
		return settlementRepository.findWithDetailsById(settlementId)
				.map(AuctionSettlementResponse::from)
				.orElseThrow(() -> new NotFoundException("경매 정산을 찾을 수 없습니다."));
	}

	public AuctionSettlementResponse rebuild(Long auctionHouseId, LocalDate auctionDate) {
		var auctionHouse = partnerReader.get(auctionHouseId);
		if (auctionHouse.getPartnerType() != PartnerType.AUCTION_HOUSE) {
			throw new IllegalArgumentException("경매장 유형 거래처만 정산할 수 있습니다.");
		}
		var settlement = settlementRepository.findByAuctionHouseIdAndAuctionDate(auctionHouseId, auctionDate)
				.orElseGet(() -> new AuctionSettlement(auctionHouse, auctionDate));
		settlement.synchronizeLines(auctionDataReader.getSoldResultLines(auctionHouseId, auctionDate));
		settlement.updateExpectedPaymentDate(paymentDateCalculator.calculate(auctionHouse, auctionDate));
		return AuctionSettlementResponse.from(settlementRepository.save(settlement));
	}

	public int rebuildExistingResults() {
		var grouped = new LinkedHashMap<SettlementKey, List<AuctionResultLine>>();
		settlementRepository.findUnsettledSoldResultLines().stream()
				.collect(java.util.stream.Collectors.groupingBy(
						line -> new SettlementKey(
								line.getAuctionAttempt().getShipmentLot().getShipment().getAuctionHouse().getId(),
								line.getAuctionDate()),
						LinkedHashMap::new,
						java.util.stream.Collectors.toList()))
				.forEach(grouped::put);
		if (grouped.isEmpty()) {
			return 0;
		}

		var auctionHouseIds = grouped.keySet().stream()
				.map(SettlementKey::auctionHouseId)
				.collect(java.util.stream.Collectors.toSet());
		var firstAuctionDate = grouped.keySet().stream().map(SettlementKey::auctionDate).min(LocalDate::compareTo).orElseThrow();
		var lastAuctionDate = grouped.keySet().stream().map(SettlementKey::auctionDate).max(LocalDate::compareTo).orElseThrow();
		Map<SettlementKey, AuctionSettlement> settlementsByKey = settlementRepository
				.findAllWithDetailsForRebuild(auctionHouseIds, firstAuctionDate, lastAuctionDate)
				.stream()
				.collect(java.util.stream.Collectors.toMap(
						settlement -> new SettlementKey(
								settlement.getAuctionHouse().getId(), settlement.getAuctionDate()),
						settlement -> settlement));
		var paymentTargets = grouped.keySet().stream()
				.map(key -> new ExpectedPaymentDateCalculator.PaymentDateTarget(
						key.auctionHouseId(), key.auctionDate()))
				.toList();
		var expectedPaymentDates = paymentDateCalculator.calculateAll(paymentTargets);
		var affectedSettlements = new ArrayList<AuctionSettlement>();
		grouped.forEach((key, newLines) -> {
			var auctionHouse = newLines.getFirst().getAuctionAttempt().getShipmentLot().getShipment().getAuctionHouse();
			var settlement = settlementsByKey.get(key);
			if (settlement == null) {
				settlement = new AuctionSettlement(auctionHouse, key.auctionDate());
			}
			var resultLinesById = new LinkedHashMap<Long, AuctionResultLine>();
			settlement.getLines().stream()
					.map(line -> line.getAuctionResultLine())
					.forEach(line -> resultLinesById.put(line.getId(), line));
			newLines.forEach(line -> resultLinesById.put(line.getId(), line));
			settlement.synchronizeLines(new ArrayList<>(resultLinesById.values()));
			settlement.updateExpectedPaymentDate(expectedPaymentDates.get(
					new ExpectedPaymentDateCalculator.PaymentDateTarget(
							key.auctionHouseId(), key.auctionDate())));
			affectedSettlements.add(settlement);
		});
		settlementRepository.saveAll(affectedSettlements);
		return grouped.size();
	}

	private record SettlementKey(Long auctionHouseId, LocalDate auctionDate) {
	}
}
