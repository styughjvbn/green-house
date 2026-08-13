package com.greenhouse.backend.sales.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.sales.domain.SalesSlipItem;
import org.junit.jupiter.api.Test;

class AuctionShipmentLotFactoryTest {

	private final AuctionShipmentLotFactory factory = new AuctionShipmentLotFactory();

	@Test
	void preservesExistingSalesItemToAuctionLotMapping() {
		SalesSlipItem item = new SalesSlipItem(null, "호접란", "팔레놉시스", "특품", 12, 0, null);

		var lot = factory.create(item);

		assertThat(lot.getItemName()).isEqualTo("팔레놉시스");
		assertThat(lot.getVarietyName()).isEqualTo("호접란");
		assertThat(lot.getShipmentGrade()).isEqualTo("특품");
		assertThat(lot.getShippedQuantity()).isEqualTo(12);
		assertThat(lot.getBoxes()).isNull();
	}

	@Test
	void usesItemNameWhenGenusIsMissing() {
		SalesSlipItem item = new SalesSlipItem(null, "호접란", null, null, 3, 0, null);

		var lot = factory.create(item);

		assertThat(lot.getItemName()).isEqualTo("호접란");
	}
}
