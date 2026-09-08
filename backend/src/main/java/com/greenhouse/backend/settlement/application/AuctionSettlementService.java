package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.auction.application.AuctionDataReader.Result;
import com.greenhouse.backend.auction.application.AuctionDataReader;
import com.greenhouse.backend.common.api.PageRequests;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.partner.application.BusinessPartnerLock;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.settlement.application.ExpectedPaymentDateCalculator.PaymentDateTarget;
import com.greenhouse.backend.settlement.domain.AuctionSettlement;
import com.greenhouse.backend.settlement.domain.AuctionSettlementLine;
import com.greenhouse.backend.settlement.domain.AuctionSettlementStatus;
import com.greenhouse.backend.settlement.dto.AuctionSettlementListItemResponse;
import com.greenhouse.backend.settlement.dto.AuctionSettlementResponse;
import com.greenhouse.backend.settlement.dto.AuctionSettlementSummaryResponse;
import com.greenhouse.backend.settlement.repository.AuctionSettlementRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AuctionSettlementService {

	private static final int RESULT_BATCH_SIZE = 500;

	private static final int LEGACY_LIST_LIMIT = 500;

	private final AuctionSettlementRepository settlementRepository;

	private final AuctionDataReader auctionDataReader;

	private final BusinessPartnerReader partnerReader;

	private final BusinessPartnerLock partnerLock;

	private final ExpectedPaymentDateCalculator paymentDateCalculator;

	private final AuctionSettlementResponseAssembler responseAssembler;

	private final Clock clock;

	@Transactional(readOnly = true)
	public List<AuctionSettlementResponse> getSettlements(Long auctionHouseId, LocalDate from, LocalDate to,
			AuctionSettlementStatus status) {
		var ids = settlementRepository.search(auctionHouseId, from, to, status, PageRequest.of(0, LEGACY_LIST_LIMIT))
			.map(AuctionSettlement::getId)
			.getContent();
		return ids.isEmpty() ? List.of()
				: responseAssembler.assembleAll(settlementRepository.findAllByIdInOrderByAuctionDateDescIdDesc(ids));
	}

	@Transactional(readOnly = true)
	public PageResponse<AuctionSettlementListItemResponse> getSettlementPage(Long auctionHouseId, LocalDate from,
			LocalDate to, AuctionSettlementStatus status, int page, int size) {
		var result = settlementRepository.search(auctionHouseId, from, to, status, PageRequests.clamped(page, size));
		var partners = partnerReader.getAllInfo(result.map(AuctionSettlement::getAuctionHouseId).getContent());
		return PageResponse.from(result.map(settlement -> AuctionSettlementListItemResponse.from(settlement,
				partners.get(settlement.getAuctionHouseId()).name())));
	}

	@Transactional(readOnly = true)
	public AuctionSettlementSummaryResponse getSummary(Long auctionHouseId, LocalDate from, LocalDate to,
			AuctionSettlementStatus status) {
		var totals = settlementRepository.summarize(auctionHouseId, from, to, status);
		return new AuctionSettlementSummaryResponse(totals.getExpectedDepositAmount(), totals.getRemainingAmount());
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
		partnerLock.lockAll(List.of(auctionHouseId));
		var settlement = settlementRepository.findByAuctionHouseIdAndAuctionDate(auctionHouseId, auctionDate)
			.orElseGet(() -> new AuctionSettlement(auctionHouseId, auctionDate));
		settlement.synchronizeLines(
				auctionDataReader.getSoldResultLines(auctionHouseId, auctionDate).stream().map(this::snapshot).toList(),
				TimeConfig.utcNow(clock));
		settlement.updateExpectedPaymentDate(paymentDateCalculator.calculate(auctionHouseId, auctionDate));
		return responseAssembler.assemble(settlementRepository.save(settlement));
	}

	public int rebuildExistingResults() {
		var grouped = groupUnsettledResults();
		if (grouped.isEmpty()) {
			return 0;
		}

		partnerLock.lockAll(grouped.keySet().stream().map(SettlementKey::auctionHouseId).distinct().toList());
		// Another initializer may have linked these candidates while this transaction
		// waited for the locks.
		var candidateIds = grouped.values().stream().flatMap(List::stream).map(Result::id).toList();
		var linkedIds = new HashSet<Long>();
		for (int start = 0; start < candidateIds.size(); start += RESULT_BATCH_SIZE) {
			linkedIds.addAll(settlementRepository.findLinkedResultIds(
					candidateIds.subList(start, Math.min(start + RESULT_BATCH_SIZE, candidateIds.size()))));
		}
		grouped.values().forEach(lines -> lines.removeIf(line -> linkedIds.contains(line.id())));
		grouped.values().removeIf(List::isEmpty);
		if (grouped.isEmpty())
			return 0;
		var settlementsByKey = loadExistingSettlements(grouped.keySet());
		var paymentTargets = grouped.keySet()
			.stream()
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
			settlement.updateExpectedPaymentDate(
					expectedPaymentDates.get(new PaymentDateTarget(key.auctionHouseId(), key.auctionDate())));
			affectedSettlements.add(settlement);
		}
		settlementRepository.saveAll(affectedSettlements);
		return grouped.size();
	}

	private Map<SettlementKey, List<Result>> groupUnsettledResults() {
		var newResults = new ArrayList<Result>();
		long afterId = 0;
		while (true) {
			var candidates = auctionDataReader.getSoldResultIdsAfter(afterId, RESULT_BATCH_SIZE);
			if (candidates.isEmpty()) {
				break;
			}
			var linkedIds = new HashSet<>(settlementRepository.findLinkedResultIds(candidates));
			var unlinkedIds = candidates.stream().filter(id -> !linkedIds.contains(id)).toList();
			newResults.addAll(auctionDataReader.getResults(unlinkedIds).values());
			afterId = candidates.getLast();
			if (candidates.size() < RESULT_BATCH_SIZE) {
				break;
			}
		}
		return newResults.stream()
			.sorted(Comparator.comparing(Result::auctionDate).thenComparing(Result::id))
			.collect(Collectors.groupingBy(line -> new SettlementKey(line.auctionHouseId(), line.auctionDate()),
					LinkedHashMap::new, Collectors.toList()));
	}

	private Map<SettlementKey, AuctionSettlement> loadExistingSettlements(Set<SettlementKey> keys) {
		var auctionHouseIds = keys.stream().map(SettlementKey::auctionHouseId).collect(Collectors.toSet());
		var firstDate = keys.stream().map(SettlementKey::auctionDate).min(LocalDate::compareTo).orElseThrow();
		var lastDate = keys.stream().map(SettlementKey::auctionDate).max(LocalDate::compareTo).orElseThrow();
		return settlementRepository.findAllWithDetailsForRebuild(auctionHouseIds, firstDate, lastDate)
			.stream()
			.collect(Collectors.toMap(
					settlement -> new SettlementKey(settlement.getAuctionHouseId(), settlement.getAuctionDate()),
					settlement -> settlement));
	}

	private List<AuctionSettlementLine> mergeResultLines(AuctionSettlement settlement, List<Result> newLines) {
		var lines = new ArrayList<>(settlement.getLines());
		newLines.stream().map(this::snapshot).forEach(lines::add);
		return lines;
	}

	private AuctionSettlementLine snapshot(Result result) {
		return new AuctionSettlementLine(result.id(), result.lotId(), result.quantity(), result.unitPrice(),
				result.amount());
	}

	private record SettlementKey(Long auctionHouseId, LocalDate auctionDate) {
	}

}
