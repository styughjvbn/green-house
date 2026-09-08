package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.auction.application.AuctionTrackingService;
import com.greenhouse.backend.auction.application.RecordAuctionResultCommand;
import com.greenhouse.backend.auction.domain.AuctionAttemptStatus;
import com.greenhouse.backend.auction.domain.AuctionResultLineInput;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.greenhouse.backend.farm.support.FarmTestFixtures;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.sales.application.SalesSlipCreationService;
import com.greenhouse.backend.sales.application.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.domain.SalesType;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Test-only input adapters exercise application contracts without adding product import
 * features.
 */
@Tag("work-e2e")
class InputChannelExtensionPostgresE2ETest extends WorkE2ETestBase {

	@Autowired
	WorkTestDataSeeder seeder;

	@Autowired
	EntityManager entityManager;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	SalesSlipCreationService sales;

	@Autowired
	AuctionTrackingService auctions;

	private long partnerId;

	private long groupId;

	private long lotId;

	@BeforeEach
	void fixture() {
		seeder.reset();
		jdbc.execute("TRUNCATE TABLE houses, business_partners, sales_slips CONTINUE IDENTITY CASCADE");
		transactions.executeWithoutResult(tx -> {
			var farm = new FarmTestFixtures(entityManager);
			var layout = farm.layout(9010);
			groupId = farm.orchidGroup(layout.left(), "CHANNEL", 100).getId();
			var partner = new BusinessPartner("Channel buyer", PartnerType.RETAIL, null, null, null, null);
			entityManager.persist(partner);
			partnerId = partner.getId();
			var market = new BusinessPartner("Channel market", PartnerType.AUCTION_HOUSE, null, null, null, null);
			entityManager.persist(market);
			var shipment = new AuctionShipment(LocalDate.of(2045, 1, 2), market.getId(), PartnerType.AUCTION_HOUSE);
			var lot = new AuctionShipmentLot("난", "CHANNEL", "A", null, 10);
			shipment.addLot(lot);
			entityManager.persist(shipment);
			lotId = lot.getId();
		});
	}

	@Test
	void csvInputReusesReservationSnapshotsAndCallerRollback() throws Exception {
		var command = fromCsv("2045-01-02,5,1200");
		var slip = sales.create(command);
		assertThat(slip.totalAmount()).isEqualTo(6000);
		assertThat(jdbc.queryForObject("SELECT quantity FROM orchid_groups WHERE id = ?", Integer.class, groupId))
			.isEqualTo(100);
		assertThat(
				jdbc.queryForObject("SELECT reserved_quantity FROM orchid_groups WHERE id = ?", Integer.class, groupId))
			.isEqualTo(5);
		assertThat(slip.items().getFirst().allocations().getFirst().creationSnapshot().quantity()).isEqualTo(100);
		assertThat(get("/api/sales-slips/" + slip.id() + "/print").data())
			.isEqualTo(get("/api/sales-slips/" + slip.id()).data());
		assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> {
			sales.create(fromCsv("2045-01-02,3,900"));
			throw new IllegalStateException("import failed after creating a slip");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_slips", Long.class)).isEqualTo(1);
		assertThat(
				jdbc.queryForObject("SELECT reserved_quantity FROM orchid_groups WHERE id = ?", Integer.class, groupId))
			.isEqualTo(5);
	}

	@Test
	void marketInputUsesTheSameLotRulesAndAtomicResultHistory() {
		assertThatThrownBy(() -> auctions.addResult(lotId, new MarketRow("2045-01-03", "9", "1200").toCommand()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("전체");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM auction_attempts", Long.class)).isZero();
		var result = auctions.addResult(lotId, new MarketRow("2045-01-03", "10", "1200").toCommand());
		assertThat(result.soldQuantity()).isEqualTo(10);
		assertThat(jdbc.queryForObject("SELECT sum(amount) FROM auction_result_lines", Long.class)).isEqualTo(12000);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM auction_lot_status_history", Long.class)).isEqualTo(1);
	}

	private SalesSlipCommand fromCsv(String line) {
		var fields = line.split(",");
		int quantity = Integer.parseInt(fields[1]);
		return new SalesSlipCommand(LocalDate.parse(fields[0]), SalesType.DIRECT, partnerId, null, null, null, null,
				null, List.of(new SalesSlipItemInput("CHANNEL", "난", null, quantity, Integer.parseInt(fields[2]), null,
						List.of(new SalesSlipAllocationInput(groupId, quantity)))));
	}

	private record MarketRow(String date, String quantity, String unitPrice) {
		RecordAuctionResultCommand toCommand() {
			return new RecordAuctionResultCommand(LocalDate.parse(date), null, AuctionAttemptStatus.SOLD, null,
					"import", List.of(new AuctionResultLineInput(null, Integer.parseInt(quantity),
							Integer.parseInt(unitPrice), null, null)));
		}
	}

}
