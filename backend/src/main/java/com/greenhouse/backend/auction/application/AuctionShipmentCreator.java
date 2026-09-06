package com.greenhouse.backend.auction.application;

import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
public class AuctionShipmentCreator {
	private final AuctionShipmentRepository auctionShipmentRepository;
	private final BusinessPartnerReader partnerReader;

	public CreatedShipment create(LocalDate shipmentDate, Long auctionHouseId, List<LotDraft> drafts) {
		var partner = partnerReader.getInfo(auctionHouseId);
		var shipment = new AuctionShipment(shipmentDate, partner.id(), partner.partnerType());
		var lotsBySourceId = new LinkedHashMap<Long, AuctionShipmentLot>();
		for (var draft : drafts) {
			var lot = new AuctionShipmentLot(draft.itemName(), draft.varietyName(), draft.shipmentGrade(),
					null, draft.quantity());
			if (draft.sourceItemId() == null || lotsBySourceId.putIfAbsent(draft.sourceItemId(), lot) != null) {
				throw new IllegalArgumentException("출하 원본 품목 식별자는 필수이며 중복될 수 없습니다.");
			}
			shipment.addLot(lot);
		}
		auctionShipmentRepository.save(shipment);
		var lotIds = new LinkedHashMap<Long, Long>();
		lotsBySourceId.forEach((sourceId, lot) -> lotIds.put(sourceId, lot.getId()));
		return new CreatedShipment(shipment.getId(), lotIds);
	}

	public record LotDraft(Long sourceItemId, String itemName, String varietyName, String shipmentGrade, Integer quantity) {
	}

	public record CreatedShipment(Long id, Map<Long, Long> lotIdsBySourceItemId) {
		public CreatedShipment {
			lotIdsBySourceItemId = Map.copyOf(lotIdsBySourceItemId);
		}
	}
}
