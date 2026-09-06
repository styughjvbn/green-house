package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.auction.application.AuctionDataReader;
import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.settlement.application.ExpectedPaymentDateCalculator.PaymentDateTarget;
import com.greenhouse.backend.settlement.domain.AuctionSettlement;
import com.greenhouse.backend.settlement.domain.AuctionSettlementStatus;
import com.greenhouse.backend.settlement.dto.AuctionSettlementResponse;
import com.greenhouse.backend.settlement.repository.AuctionSettlementRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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
	private final AuctionSettlementResponseAssembler responseAssembler;
	private final Clock clock;

	@Transactional(readOnly = true)
	public List<AuctionSettlementResponse> getSettlements(
			Long auctionHouseId,
			LocalDate from,
			LocalDate to,
			AuctionSettlementStatus status) {
		return responseAssembler.assembleAll(settlementRepository.search(auctionHouseId, from, to, status));
	}

	@Transactional(readOnly = true)
	public AuctionSettlementResponse getSettlement(Long settlementId) {
		return settlementRepository.findWithDetailsById(settlementId)
				.map(responseAssembler::assemble)
				.orElseThrow(() -> new NotFoundException("경매 정산을 찾을 수 없습니다."));
	}

	public AuctionSettlementResponse rebuild(Long auctionHouseId, LocalDate auctionDate) {
		var auctionHouse = partnerReader.getInfo(auctionHouseId);
		if (auctionHouse.partnerType() != PartnerType.AUCTION_HOUSE) {
			throw new IllegalArgumentException("경매장 유형 거래처만 정산할 수 있습니다.");
		}
		var settlement = settlementRepository.findByAuctionHouseIdAndAuctionDate(auctionHouseId, auctionDate)
				.orElseGet(() -> new AuctionSettlement(auctionHouseId, auctionDate));
		settlement.synchronizeLines(auctionDataReader.getSoldResultLines(auctionHouseId, auctionDate),
				TimeConfig.utcNow(clock));
		settlement.updateExpectedPaymentDate(paymentDateCalculator.calculate(auctionHouseId, auctionDate));
		return responseAssembler.assemble(settlementRepository.save(settlement));
	}

	public int rebuildExistingResults() {
		var grouped = groupUnsettledResults();
		if (grouped.isEmpty()) {
			return 0;
		}

		var settlementsByKey = loadExistingSettlements(grouped.keySet());
		var paymentTargets = grouped.keySet().stream()
				.map(key -> new PaymentDateTarget(key.auctionHouseId(), key.auctionDate()))
				.toList();
		var expectedPaymentDates = paymentDateCalculator.calculateAll(paymentTargets);
		var affectedSettlements = new ArrayList<AuctionSettlement>();
		var receivedAt = TimeConfig.utcNow(clock);
		for (var entry : grouped.entrySet()) {
			var key = entry.getKey();
			var settlement = settlementsByKey.computeIfAbsent(key,
					missing -> new AuctionSettlement(missing.auctionHouseId(), missing.auctionDate()));
			settlement.synchronizeLines(mergeResultLines(settlement, entry.getValue()), receivedAt);
			settlement.updateExpectedPaymentDate(expectedPaymentDates.get(
					new PaymentDateTarget(key.auctionHouseId(), key.auctionDate())));
			affectedSettlements.add(settlement);
		}
		settlementRepository.saveAll(affectedSettlements);
		return grouped.size();
	}

	private Map<SettlementKey, List<AuctionResultLine>> groupUnsettledResults() {
		return settlementRepository.findUnsettledSoldResultLines().stream()
				.collect(Collectors.groupingBy(
						line -> new SettlementKey(
								line.getAuctionAttempt().getShipmentLot().getShipment().getAuctionHouseId(),
								line.getAuctionDate()),
						LinkedHashMap::new, Collectors.toList()));
	}

	private Map<SettlementKey, AuctionSettlement> loadExistingSettlements(Set<SettlementKey> keys) {
		var auctionHouseIds = keys.stream().map(SettlementKey::auctionHouseId).collect(Collectors.toSet());
		var firstDate = keys.stream().map(SettlementKey::auctionDate).min(LocalDate::compareTo).orElseThrow();
		var lastDate = keys.stream().map(SettlementKey::auctionDate).max(LocalDate::compareTo).orElseThrow();
		return settlementRepository.findAllWithDetailsForRebuild(auctionHouseIds, firstDate, lastDate).stream()
				.collect(Collectors.toMap(
						settlement -> new SettlementKey(settlement.getAuctionHouseId(), settlement.getAuctionDate()),
						settlement -> settlement));
	}

	private List<AuctionResultLine> mergeResultLines(AuctionSettlement settlement, List<AuctionResultLine> newLines) {
		var linesById = new LinkedHashMap<Long, AuctionResultLine>();
		for (var line : settlement.getLines()) {
			var result = line.getAuctionResultLine();
			linesById.put(result.getId(), result);
		}
		newLines.forEach(line -> linesById.put(line.getId(), line));
		return new ArrayList<>(linesById.values());
	}

	private record SettlementKey(Long auctionHouseId, LocalDate auctionDate) {
	}
}
