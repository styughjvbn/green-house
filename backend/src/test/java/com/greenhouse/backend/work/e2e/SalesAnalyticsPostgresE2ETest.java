package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;

import com.greenhouse.backend.analytics.application.AnalyticsQueryService;
import com.greenhouse.backend.analytics.dto.AnalyticsRankedValueResponse;
import com.greenhouse.backend.analytics.dto.PartnerAnalyticsStatResponse;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.sales.application.SalesMetricsReader;
import com.greenhouse.backend.sales.domain.SalesPaymentCategory;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.settlement.domain.PartnerBalanceSummary;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@Transactional
class SalesAnalyticsPostgresE2ETest extends WorkE2ETestBase {

	private static final LocalDate FROM = LocalDate.of(2040, 7, 1);

	private static final LocalDate TO = LocalDate.of(2040, 7, 31);

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private AnalyticsQueryService analytics;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoSpyBean
	private SalesMetricsReader salesMetrics;

	private int slipSequence;

	@Test
	void combinesLegacyPaymentLabelsWithoutChangingStoredAmountsOrStatuses() {
		var partner = partner("Payment categories");
		var labels = new String[] { "입금 완료", "PAID", "미완료", "부분입금", "부분 처리 완료", "미입금", "정산 대기", "PARTIALLY_PAID",
				"paid", " PAID ", "" };
		for (int index = 0; index < labels.length; index++) {
			var slip = new SalesSlip("PAYMENT-CATEGORY-" + index, FROM, SalesType.DIRECT, null, partner.getId(),
					labels[index], "출고 완료", null, null);
			slip.addItem(new SalesSlipItem(null, "Variety", "난", null, 1, 1_000_000_000, null));
			entityManager.persist(slip);
		}
		entityManager.flush();
		entityManager.clear();

		assertThat(salesMetrics.paymentBreakdown(FROM, TO))
			.containsExactlyInAnyOrderEntriesOf(Map.of(SalesPaymentCategory.PAID, 3_000_000_000L,
					SalesPaymentCategory.PARTIAL, 2_000_000_000L, SalesPaymentCategory.UNPAID, 6_000_000_000L));
		var result = analytics.getSalesAnalytics(FROM, TO);
		assertThat(result.paymentBreakdown()).containsExactly(new AnalyticsRankedValueResponse("입금 완료", 3_000_000_000L),
				new AnalyticsRankedValueResponse("부분입금", 2_000_000_000L),
				new AnalyticsRankedValueResponse("미입금", 6_000_000_000L));
		assertThat(result.unpaidAmount()).isEqualTo(11_000_000_000L);
		assertThat(jdbc.queryForList("select payment_status from sales_slips where partner_id = ?", String.class,
				partner.getId()))
			.containsExactlyInAnyOrder(labels);
	}

	@Test
	void returnsEmptySalesAndPartnerSummariesWithoutCreatingBalances() {
		var sales = analytics.getSalesAnalytics(FROM, TO);
		assertThat(sales.currentMonthSales()).isZero();
		assertThat(sales.shippedQuantity()).isZero();
		assertThat(sales.unpaidAmount()).isZero();
		assertThat(sales.partnerSales()).isEmpty();
		assertThat(sales.recentSlips()).isEmpty();
		assertThat(sales.unpaidSlips()).isEmpty();
		assertThat(sales.monthlySales()).hasSize(6).allSatisfy(row -> assertThat(row.value()).isZero());
		assertThat(analytics.getPartnerAnalytics(FROM, TO).partnerStats()).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from partner_balance_summaries", Long.class)).isZero();
	}

	@Test
	void includesAuctionAndZeroAmountCompletedSlipsButExcludesCanceledSlips() {
		var house = partner("Auction");
		house.update("Auction", PartnerType.AUCTION_HOUSE, null, null, null, null);
		var auction = new SalesSlip("ANALYTICS-AUCTION", TO, SalesType.AUCTION, null, house.getId(), "정산 대기", "출하 완료",
				null, null);
		auction.addItem(new SalesSlipItem(null, "Variety", "난", null, 10, 100, null));
		entityManager.persist(auction);
		var zero = partner("Zero sale");
		slip(zero, FROM, 1, 0);
		var canceled = slip(zero, TO, 1, 9_999);
		canceled.updateSalesStatus("취소");
		entityManager.flush();
		entityManager.clear();

		var sales = analytics.getSalesAnalytics(FROM, TO);

		assertThat(sales.currentMonthSales()).isEqualTo(1_000);
		assertThat(sales.shippedQuantity()).isEqualTo(11);
		assertThat(sales.unpaidAmount()).isEqualTo(1_000);
		assertThat(sales.paymentBreakdown().getLast()).isEqualTo(new AnalyticsRankedValueResponse("미입금", 1_000L));
		assertThat(sales.recentSlips()).hasSize(2);
		assertThat(analytics.getPartnerAnalytics(FROM, TO).partnerStats().getLast())
			.isEqualTo(new PartnerAnalyticsStatResponse(zero.getId(), "Zero sale", PartnerType.WHOLESALE, 0L, 1L, 0L,
					0L, 0L, 0L, 0L, FROM));
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 10, 50, 501 })
	void aggregatesLongAmountsWithoutEntityLoadsAndBatchesPartnerIdentities(int size) {
		for (int index = 0; index < size; index++) {
			var partner = partner("Scale " + index);
			slip(partner, FROM, 1, 1_500_000_000);
			slip(partner, TO, 1, 1_500_000_000);
			balance(partner, 700, 0, 0);
		}
		entityManager.flush();
		entityManager.clear();
		var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		var sales = analytics.getSalesAnalytics(FROM, TO);

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(12 + (size + 499) / 500);
		assertThat(statistics.getEntityLoadCount()).isZero();
		assertThat(sales.currentMonthSales()).isEqualTo(3_000_000_000L * size);
		assertThat(sales.shippedQuantity()).isEqualTo(2L * size);
		assertThat(sales.partnerSales()).hasSize(Math.min(10, size))
			.allSatisfy(row -> assertThat(row.value()).isEqualTo(3_000_000_000L));
		assertThat(sales.recentSlips()).hasSize(Math.min(5, 2 * size));
		statistics.clear();

		var partners = analytics.getPartnerAnalytics(FROM, TO);

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(2 + (size + 499) / 500);
		assertThat(statistics.getEntityLoadCount()).isZero();
		assertThat(partners.partnerStats()).hasSize(size).allSatisfy(row -> {
			assertThat(row.totalSales()).isEqualTo(3_000_000_000L);
			assertThat(row.transactionCount()).isEqualTo(2);
			assertThat(row.receivableBalance()).isEqualTo(700);
		});
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void joinedMetricsUseOneSnapshotWhenAnotherTransactionChangesSalesNamesAndBalances() {
		var transaction = new TransactionTemplate(transactionManager);
		transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		Long partnerId = transaction.execute(status -> {
			var partner = partner("Snapshot before");
			slip(partner, FROM, 1, 300);
			balance(partner, 700, 0, 0);
			return partner.getId();
		});
		try {
			doAnswer(invocation -> {
				var result = invocation.callRealMethod();
				assertThat(jdbc.queryForObject("show transaction_isolation", String.class))
					.isEqualTo("repeatable read");
				transaction.executeWithoutResult(status -> {
					var partner = entityManager.find(BusinessPartner.class, partnerId);
					partner.update("Snapshot after", PartnerType.RETAIL, null, null, null, null);
					slip(partner, TO, 1, 500);
					jdbc.update("update partner_balance_summaries set receivable_balance = 1200 where partner_id = ?",
							partnerId);
				});
				return result;
			}).when(salesMetrics).partnerSales(FROM, TO);

			var before = analytics.getPartnerAnalytics(FROM, TO);

			assertThat(before.partnerStats()).containsExactly(new PartnerAnalyticsStatResponse(partnerId,
					"Snapshot before", PartnerType.WHOLESALE, 300L, 1L, 300L, 0L, 700L, 0L, 0L, FROM));
			doCallRealMethod().when(salesMetrics).partnerSales(FROM, TO);
			var after = analytics.getPartnerAnalytics(FROM, TO);
			assertThat(after.partnerStats()).containsExactly(new PartnerAnalyticsStatResponse(partnerId,
					"Snapshot after", PartnerType.RETAIL, 800L, 2L, 800L, 0L, 1200L, 0L, 0L, TO));
		}
		finally {
			transaction.executeWithoutResult(status -> {
				jdbc.update(
						"delete from sales_slip_items where sales_slip_id in (select id from sales_slips where partner_id = ?)",
						partnerId);
				jdbc.update("delete from sales_slips where partner_id = ?", partnerId);
				jdbc.update("delete from partner_balance_summaries where partner_id = ?", partnerId);
				jdbc.update("delete from business_partners where id = ?", partnerId);
			});
		}
	}

	@Test
	void partnerStatsRetainCurrentBalancesAndPostgresOrderingWithoutPeriodSales() {
		var sold = partner("Old name");
		var first = slip(sold, FROM, 1, 300);
		first.recordPayment(100L);
		slip(sold, TO, 2, 100);
		slip(sold, FROM.minusDays(1), 1, 9_999);
		balance(sold, 700, 0, 0);
		sold.update("Current name", PartnerType.RETAIL, null, null, null, null);
		ReflectionTestUtils.setField(sold, "active", false);
		var creditOnly = partner("Credit only");
		balance(creditOnly, 0, 90, 0);
		var unappliedOnly = partner("Unapplied only");
		balance(unappliedOnly, 0, 0, 80);
		var zero = partner("Zero balance");
		balance(zero, 0, 0, 0);
		var negative = partner("Negative only");
		balance(negative, 0, -20, 0);
		entityManager.flush();
		entityManager.clear();

		var result = analytics.getPartnerAnalytics(FROM, TO);

		assertThat(result.partnerStats()).hasSize(3);
		// PostgreSQL DESC places the original left-join NULL sales totals first.
		assertThat(result.partnerStats().subList(0, 2)).extracting(PartnerAnalyticsStatResponse::partnerId)
			.containsExactlyInAnyOrder(creditOnly.getId(), unappliedOnly.getId());
		assertThat(result.partnerStats().getLast()).isEqualTo(new PartnerAnalyticsStatResponse(sold.getId(),
				"Current name", PartnerType.RETAIL, 500L, 2L, 400L, 100L, 700L, 0L, 0L, TO));
		assertThat(result.partnerStats().getFirst().latestSaleDate()).isNull();
		assertThat(result.partnerSales().getFirst().value()).isZero();
	}

	@Test
	void salesRankingMergesSameNamesBeforeSelectingTheGlobalTopTen() {
		for (int index = 0; index < 11; index++) {
			slip(partner("Partner " + index), FROM, 1, 100 + index);
		}
		slip(partner("Shared name"), FROM, 1, 60);
		var renamed = partner("Before rename");
		slip(renamed, TO, 1, 60);
		renamed.update("Shared name", PartnerType.RETAIL, null, null, null, null);
		entityManager.flush();
		entityManager.clear();

		var result = analytics.getSalesAnalytics(FROM, TO);

		assertThat(result.partnerSales()).hasSize(10);
		assertThat(result.partnerSales().getFirst()).isEqualTo(new AnalyticsRankedValueResponse("Shared name", 120L));
		assertThat(result.partnerSales()).extracting(AnalyticsRankedValueResponse::value)
			.containsExactly(120L, 110L, 109L, 108L, 107L, 106L, 105L, 104L, 103L, 102L);
		assertThat(result.recentSlips().getFirst().partnerName()).isEqualTo("Shared name");
		assertThat(result.recentSlips()).hasSize(5);
		assertThat(result.unpaidSlips()).hasSize(5);
	}

	@Test
	void comparesClampedMonthEndsAndCountsItemsWithoutMultiplyingSlipAmounts() {
		var partner = partner("Month end");
		var from = LocalDate.of(2040, 3, 30);
		var to = LocalDate.of(2040, 3, 31);
		var partial = slip(partner, from, 2, 100);
		partial.addItem(new SalesSlipItem(null, "Other variety", "난", null, 3, 200, null));
		partial.recordPayment(100L);
		var paid = slip(partner, to, 1, 100);
		paid.recordPayment(100L);
		slip(partner, LocalDate.of(2040, 2, 29), 1, 300);
		slip(partner, LocalDate.of(2040, 2, 28), 1, 9_999);
		var draft = new SalesSlip("ANALYTICS-DRAFT", to, SalesType.DIRECT, null, partner.getId(), "미입금", "작성중", null,
				null);
		draft.addItem(new SalesSlipItem(null, "Excluded", null, null, 1, 9_999, null));
		entityManager.persist(draft);
		entityManager.flush();
		entityManager.clear();

		var result = analytics.getSalesAnalytics(from, to);

		assertThat(result.currentMonthSales()).isEqualTo(900);
		assertThat(result.previousMonthSales()).isEqualTo(300);
		assertThat(result.shippedQuantity()).isEqualTo(6);
		assertThat(result.previousMonthShippedQuantity()).isEqualTo(1);
		assertThat(result.unpaidAmount()).isEqualTo(700);
		assertThat(result.monthlySales()).hasSize(6);
		assertThat(result.monthlySales().getLast().value()).isEqualTo(900);
		assertThat(result.monthlySales().get(4).value()).isZero();
		assertThat(result.paymentBreakdown()).containsExactly(new AnalyticsRankedValueResponse("입금 완료", 100L),
				new AnalyticsRankedValueResponse("부분입금", 800L), new AnalyticsRankedValueResponse("미입금", 0L));
		assertThat(result.varietySales()).containsExactly(new AnalyticsRankedValueResponse("Other variety", 600L),
				new AnalyticsRankedValueResponse("Variety", 300L));
	}

	private BusinessPartner partner(String name) {
		var partner = new BusinessPartner(name, PartnerType.WHOLESALE, null, null, null, null);
		entityManager.persist(partner);
		return partner;
	}

	private SalesSlip slip(BusinessPartner partner, LocalDate date, int quantity, int price) {
		var slip = new SalesSlip("ANALYTICS-" + (++slipSequence), date, SalesType.DIRECT, null, partner.getId(), "미입금",
				"출고 완료", null, null);
		slip.addItem(new SalesSlipItem(null, "Variety", "난", null, quantity, price, null));
		entityManager.persist(slip);
		return slip;
	}

	private void balance(BusinessPartner partner, long receivable, long credit, long unapplied) {
		var balance = new PartnerBalanceSummary(partner.getId());
		balance.updateReceivableBalance(receivable, null);
		ReflectionTestUtils.setField(balance, "creditBalance", credit);
		ReflectionTestUtils.setField(balance, "unappliedPaymentAmount", unapplied);
		entityManager.persist(balance);
	}

}
