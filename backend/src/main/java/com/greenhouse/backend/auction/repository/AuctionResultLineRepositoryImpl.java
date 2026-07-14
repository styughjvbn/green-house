package com.greenhouse.backend.auction.repository;

import static com.greenhouse.backend.auction.domain.QAuctionAttempt.auctionAttempt;
import static com.greenhouse.backend.auction.domain.QAuctionResultLine.auctionResultLine;
import static com.greenhouse.backend.auction.domain.QAuctionShipment.auctionShipment;
import static com.greenhouse.backend.auction.domain.QAuctionShipmentLot.auctionShipmentLot;
import static com.greenhouse.backend.partner.domain.QBusinessPartner.businessPartner;

import com.greenhouse.backend.auction.domain.AuctionResultLine;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

public class AuctionResultLineRepositoryImpl implements AuctionResultLineRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	public AuctionResultLineRepositoryImpl(EntityManager entityManager) {
		this.queryFactory = new JPAQueryFactory(entityManager);
	}

	@Override
	public List<AuctionResultLine> findSoldLines(Long auctionHouseId, LocalDate auctionDate) {
		return soldLineBaseQuery()
				.where(
						auctionResultLine.amount.gt(0),
						auctionResultLine.auctionDate.eq(auctionDate),
						businessPartner.id.eq(auctionHouseId))
				.orderBy(auctionResultLine.id.asc())
				.fetch();
	}

	@Override
	public List<AuctionResultLine> findAllSoldLines() {
		return soldLineBaseQuery()
				.where(auctionResultLine.amount.gt(0))
				.orderBy(auctionResultLine.auctionDate.asc(), auctionResultLine.id.asc())
				.fetch();
	}

	private JPAQuery<AuctionResultLine> soldLineBaseQuery() {
		return queryFactory
				.selectFrom(auctionResultLine)
				.join(auctionResultLine.auctionAttempt, auctionAttempt).fetchJoin()
				.join(auctionAttempt.shipmentLot, auctionShipmentLot).fetchJoin()
				.join(auctionShipmentLot.shipment, auctionShipment).fetchJoin()
				.join(auctionShipment.auctionHouse, businessPartner).fetchJoin();
	}
}
