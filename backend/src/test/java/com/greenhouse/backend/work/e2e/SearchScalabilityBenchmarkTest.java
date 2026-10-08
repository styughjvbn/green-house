package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.sales.api.document.SalesSlipSummary;
import com.greenhouse.backend.sales.application.auction.AuctionTrackingService;
import com.greenhouse.backend.sales.application.document.SalesQueryService;
import com.greenhouse.backend.sales.dto.auction.AuctionLotResponse;
import com.greenhouse.backend.support.JdbcMeasurement;
import com.sun.management.ThreadMXBean;
import jakarta.persistence.EntityManagerFactory;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-benchmark")
@Import({QueryShapeCapture.Configuration.class, JdbcMeasurement.Configuration.class})
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class SearchScalabilityBenchmarkTest extends WorkE2ETestBase {

  @Autowired JdbcTemplate jdbc;

  @Autowired SalesQueryService sales;

  @Autowired AuctionTrackingService auctions;

  @Autowired EntityManagerFactory entityManagerFactory;

  @Autowired QueryShapeCapture capture;
  @Autowired JdbcMeasurement jdbcMeasurement;

  @Test
  void preservesGlobalPaginationWithThousandsOfMatchingPartners() throws Exception {
    var allocations = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    List<Map<String, Object>> samples = new ArrayList<>();
    for (int count : List.of(501, 5001, 70001)) {
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
      jdbc.update(
          """
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
      capture.start();
      jdbcMeasurement.start();
      long allocatedBefore = allocations.getThreadAllocatedBytes(Thread.currentThread().threadId());
      long started = System.nanoTime();
      PageResponse<SalesSlipSummary> page;
      JdbcMeasurement.Sample salesJdbc;
      try {
        page =
            sales.getSalesSlipPage(
                null, null, null, null, null, "Scalability Contact", count / 100, 100);
      } finally {
        salesJdbc = jdbcMeasurement.stop();
      }
      long salesNanos = System.nanoTime() - started;
      long salesBytes =
          allocations.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocatedBefore;
      long salesQueries = stats.getPrepareStatementCount();
      long salesParameters = QueryShapeCapture.maxParameters(capture.stop());
      assertThat(page.totalElements()).isEqualTo(count);
      assertThat(page.content()).hasSize(1);
      assertThat(salesQueries).isEqualTo(count / 500 + 4);
      assertThat(salesParameters).isLessThanOrEqualTo(500);
      for (int spaces : List.of(1, 20)) {
        String words = "word ".repeat(spaces - 1);
        String keyword = "white " + words + "scalability";
        jdbc.update(
            "update auction_shipment_lots set variety_name = ?", ("Snow White " + words).trim());
        auctions.getLots(
            null, null, null, null, null, null, false, false, false, keyword, count / 100, 100);
        stats.clear();
        capture.start();
        jdbcMeasurement.start();
        allocatedBefore = allocations.getThreadAllocatedBytes(Thread.currentThread().threadId());
        started = System.nanoTime();
        PageResponse<AuctionLotResponse> lots;
        JdbcMeasurement.Sample auctionJdbc;
        try {
          lots =
              auctions.getLots(
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  false,
                  false,
                  false,
                  keyword,
                  count / 100,
                  100);
        } finally {
          auctionJdbc = jdbcMeasurement.stop();
        }
        long auctionNanos = System.nanoTime() - started;
        long auctionBytes =
            allocations.getThreadAllocatedBytes(Thread.currentThread().threadId())
                - allocatedBefore;
        long auctionQueries = stats.getPrepareStatementCount();
        long auctionParameters = QueryShapeCapture.maxParameters(capture.stop());
        assertThat(lots.totalElements()).isEqualTo(count);
        assertThat(lots.content()).hasSize(1);
        assertThat(auctionQueries).isEqualTo(count / 500 + 6);
        assertThat(auctionParameters).isLessThanOrEqualTo(500);
        var sample = new LinkedHashMap<String, Object>();
        sample.put("matchingPartners", count);
        sample.put("keywordSpaces", spaces);
        sample.put("salesQueries", salesQueries);
        sample.put("auctionQueries", auctionQueries);
        sample.put("salesJdbc", salesJdbc);
        sample.put("auctionJdbc", auctionJdbc);
        sample.put("salesMaxParameters", salesParameters);
        sample.put("auctionMaxParameters", auctionParameters);
        sample.put("salesAllocatedBytes", salesBytes);
        sample.put("auctionAllocatedBytes", auctionBytes);
        sample.put("salesElapsedMs", salesNanos / 1000000.0);
        sample.put("auctionElapsedMs", auctionNanos / 1000000.0);
        samples.add(sample);
      }
      // A local scalar ownership predicate, not the complete API query plan.
      var ids = LongStream.rangeClosed(1, count).map(n -> 1000000 + n).boxed().toArray(Long[]::new);
      String plan =
          jdbc.execute(
              (ConnectionCallback<String>)
                  connection -> {
                    var array = connection.createArrayOf("bigint", ids);
                    try (var statement =
                        connection.prepareStatement(
                            "explain (analyze, buffers, format json) select id from sales_slips where partner_id = any (?) order by sale_date desc, id desc offset ? limit 100")) {
                      statement.setArray(1, array);
                      statement.setInt(2, count / 100 * 100);
                      try (var result = statement.executeQuery()) {
                        result.next();
                        return result.getString(1);
                      }
                    } finally {
                      array.free();
                    }
                  });
      var planPath = Path.of("build/work-benchmark/partner-search-plan-" + count + ".json");
      Files.createDirectories(planPath.getParent());
      Files.writeString(planPath, plan);
    }
    var path = Path.of("build/work-benchmark/partner-search.json");
    Files.createDirectories(path.getParent());
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), samples);
  }
}
