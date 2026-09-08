package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.auction.application.AuctionTrackingService;
import com.greenhouse.backend.sales.application.SalesQueryService;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-benchmark")
class SearchScalabilityBenchmarkTest extends WorkE2ETestBase {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	SalesQueryService sales;

	@Autowired
	AuctionTrackingService auctions;

	@Autowired
	EntityManagerFactory entityManagerFactory;

	@Test
	void preservesGlobalPaginationWithThousandsOfMatchingPartners() throws Exception {
		var allocations = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
		var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		List<Map<String, Object>> samples = new ArrayList<>();
		for (int count : List.of(501, 5001)) {
			jdbc.execute("TRUNCATE TABLE business_partners, sales_slips CONTINUE IDENTITY CASCADE");
			jdbc.update(
					"""
							INSERT INTO business_partners (id, name, partner_type, owner_name, is_active, created_at, updated_at)
							SELECT 1000000 + n, 'Scalability Market ' || n, 'AUCTION_HOUSE', 'Scalability Contact', true, now(), now()
							FROM generate_series(1, ?) n
							""",
					count);
			jdbc.update(
					"""
							INSERT INTO sales_slips (id, slip_number, sale_date, sales_type, partner_id, payment_status, sales_status,
							 total_amount, paid_amount, version, created_at, updated_at)
							SELECT id, 'SCALE-' || id, DATE '2045-01-02', 'DIRECT', id, '미입금', '작성중', 0, 0, 0, now(), now()
							FROM business_partners
							""");
			jdbc.update("""
					INSERT INTO auction_shipments (id, shipment_date, auction_house_id, status, created_at, updated_at)
					SELECT id, DATE '2045-01-02', id, 'SHIPPED', now(), now() FROM business_partners
					""");
			jdbc.update(
					"""
							INSERT INTO auction_shipment_lots (id, shipment_id, item_name, variety_name, shipped_quantity,
							 sold_quantity, waiting_quantity, returned_quantity, current_status, version, created_at, updated_at)
							SELECT id, id, 'Orchid', 'Snow White', 10, 0, 10, 0, 'WAITING', 0, now(), now() FROM business_partners
							""");
			// Warm the query plans; measure complete result semantics, not only one DB
			// request.
			sales.getSalesSlipPage(null, null, null, null, null, "Scalability Contact", count / 100, 100);
			stats.clear();
			long allocatedBefore = allocations.getThreadAllocatedBytes(Thread.currentThread().threadId());
			long started = System.nanoTime();
			var page = sales.getSalesSlipPage(null, null, null, null, null, "Scalability Contact", count / 100, 100);
			long salesNanos = System.nanoTime() - started;
			long salesBytes = allocations.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocatedBefore;
			long salesQueries = stats.getPrepareStatementCount();
			assertThat(page.totalElements()).isEqualTo(count);
			assertThat(page.content()).hasSize(1);
			assertThat(salesQueries).isEqualTo(count / 500 + 4);
			auctions.getLots(null, null, null, null, null, null, false, false, false, "White Scalability", count / 100,
					100);
			stats.clear();
			allocatedBefore = allocations.getThreadAllocatedBytes(Thread.currentThread().threadId());
			started = System.nanoTime();
			var lots = auctions.getLots(null, null, null, null, null, null, false, false, false, "White Scalability",
					count / 100, 100);
			long auctionNanos = System.nanoTime() - started;
			long auctionBytes = allocations.getThreadAllocatedBytes(Thread.currentThread().threadId())
					- allocatedBefore;
			long auctionQueries = stats.getPrepareStatementCount();
			assertThat(lots.totalElements()).isEqualTo(count);
			assertThat(lots.content()).hasSize(1);
			assertThat(auctionQueries).isEqualTo(count / 500 + 7);
			var sample = new LinkedHashMap<String, Object>();
			sample.put("matchingPartners", count);
			sample.put("salesQueries", salesQueries);
			sample.put("auctionQueries", auctionQueries);
			sample.put("salesAllocatedBytes", salesBytes);
			sample.put("auctionAllocatedBytes", auctionBytes);
			sample.put("salesElapsedMs", salesNanos / 1000000.0);
			sample.put("auctionElapsedMs", auctionNanos / 1000000.0);
			samples.add(sample);
		}
		var path = Path.of("build/work-benchmark/partner-search.json");
		Files.createDirectories(path.getParent());
		objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), samples);
	}

}
