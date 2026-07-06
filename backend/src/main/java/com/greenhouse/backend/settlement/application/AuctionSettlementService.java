package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.settlement.domain.AuctionSettlement;
import com.greenhouse.backend.settlement.domain.AuctionSettlementStatus;
import com.greenhouse.backend.settlement.dto.AuctionSettlementResponse;
import com.greenhouse.backend.settlement.repository.AuctionSettlementRepository;
import com.greenhouse.backend.settlement.repository.SettlementResultLineRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuctionSettlementService {
	private final AuctionSettlementRepository settlementRepository;
	private final SettlementResultLineRepository resultLineRepository;
	private final BusinessPartnerRepository partnerRepository;

	public AuctionSettlementService(
		AuctionSettlementRepository settlementRepository,
		SettlementResultLineRepository resultLineRepository,
		BusinessPartnerRepository partnerRepository
	) {
		this.settlementRepository = settlementRepository;
		this.resultLineRepository = resultLineRepository;
		this.partnerRepository = partnerRepository;
	}

	@Transactional(readOnly = true)
	public List<AuctionSettlementResponse> getSettlements(
		Long auctionHouseId,
		LocalDate from,
		LocalDate to,
		AuctionSettlementStatus status
	) {
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
		var auctionHouse = partnerRepository.findById(auctionHouseId)
			.orElseThrow(() -> new NotFoundException("경매장을 찾을 수 없습니다."));
		if (auctionHouse.getPartnerType() != PartnerType.AUCTION_HOUSE) {
			throw new IllegalArgumentException("경매장 유형 거래처만 정산할 수 있습니다.");
		}
		var settlement = settlementRepository.findByAuctionHouseIdAndAuctionDate(auctionHouseId, auctionDate)
			.orElseGet(() -> new AuctionSettlement(auctionHouse, auctionDate));
		settlement.synchronizeLines(resultLineRepository.findSoldLines(auctionHouseId, auctionDate));
		return AuctionSettlementResponse.from(settlementRepository.save(settlement));
	}

	public int rebuildExistingResults() {
		var grouped = new LinkedHashMap<SettlementKey, List<AuctionResultLine>>();
		resultLineRepository.findAllSoldLines().stream()
			.collect(java.util.stream.Collectors.groupingBy(
				line -> new SettlementKey(
					line.getAuctionAttempt().getShipmentLot().getShipment().getAuctionHouse().getId(),
					line.getAuctionDate()),
				LinkedHashMap::new,
				java.util.stream.Collectors.toList()))
			.forEach(grouped::put);

		grouped.forEach((key, lines) -> {
			var auctionHouse = lines.getFirst().getAuctionAttempt().getShipmentLot().getShipment().getAuctionHouse();
			var settlement = settlementRepository.findByAuctionHouseIdAndAuctionDate(key.auctionHouseId(), key.auctionDate())
				.orElseGet(() -> new AuctionSettlement(auctionHouse, key.auctionDate()));
			settlement.synchronizeLines(lines);
			settlementRepository.save(settlement);
		});
		return grouped.size();
	}

	private record SettlementKey(Long auctionHouseId, LocalDate auctionDate) { }
}
