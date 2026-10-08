package com.greenhouse.backend.sales.api.document;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public interface SalesMetricsApi {

  long sumSales(LocalDate from, LocalDate to);

  long sumShippedQuantity(LocalDate from, LocalDate to);

  long sumUnpaidAmount(LocalDate from, LocalDate to);

  Map<YearMonth, Long> monthlySales(LocalDate from, LocalDate to);

  List<NamedAmount> varietySales(LocalDate from, LocalDate to);

  Map<Long, PartnerSales> partnerSales(LocalDate from, LocalDate to);

  Map<SalesPaymentCategory, Long> paymentBreakdown(LocalDate from, LocalDate to);

  List<SlipSummary> recentSlips(LocalDate from, LocalDate to);

  List<SlipSummary> unpaidSlips(LocalDate from, LocalDate to);

  public record NamedAmount(String name, long amount) {}

  public record PartnerSales(
      Long partnerId,
      long totalSales,
      long transactionCount,
      long unpaidAmount,
      long paidAmount,
      LocalDate latestSaleDate) {}

  public record SlipSummary(
      Long id,
      String slipNumber,
      LocalDate saleDate,
      Long partnerId,
      Integer totalAmount,
      Long paidAmount,
      Long remainingAmount,
      String paymentStatus,
      String salesStatus) {}
}
