package com.greenhouse.backend.support;

import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.direct.domain.DirectSaleAmounts;
import com.greenhouse.backend.sales.document.domain.SalesSlip;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit historical terms for tests that seed a document without its creation use case. */
public final class DirectSaleFixtures {
  private DirectSaleFixtures() {}

  public static void refreshProjection(SalesSlip document) {
    if (document.getSalesType() != SalesType.DIRECT) return;
    int total =
        DirectSaleAmounts.total(
            document.getItems().stream().map(item -> item.getAmount()).toList());
    document.applyFinancialProjection(
        total,
        document.getExpectedPaymentDate(),
        document.getPaymentMethod(),
        document.getPaidAmount(),
        DirectSaleAmounts.remaining(total, document.getPaidAmount()),
        document.getPaymentStatus());
  }

  public static void projectAllocation(SalesSlip document, long allocated) {
    document.applyFinancialProjection(
        document.getTotalAmount(),
        document.getExpectedPaymentDate(),
        document.getPaymentMethod(),
        allocated,
        DirectSaleAmounts.remaining(document.getTotalAmount(), allocated),
        DirectSaleAmounts.paymentStatus(document.getTotalAmount(), BigDecimal.valueOf(allocated)));
  }

  public static void copyAllTerms(JdbcTemplate jdbc) {
    jdbc.update(
        """
      insert into direct_sales (sales_slip_id,version,partner_id,sale_date,total_amount,
          expected_payment_date,payment_method,unpaid_payment_label,created_at,updated_at)
      select id,0,partner_id,sale_date,total_amount,expected_payment_date,payment_method,payment_status,created_at,updated_at
      from sales_slips document where sales_type='DIRECT'
          and not exists(select 1 from direct_sales sale where sale.sales_slip_id=document.id)
      """);
    jdbc.update(
        """
      insert into direct_sale_prices (sales_slip_item_id,sales_slip_id,priced_quantity,unit_price,amount)
      select id,sales_slip_id,quantity,unit_price,amount from sales_slip_items item
      where exists(select 1 from direct_sales sale where sale.sales_slip_id=item.sales_slip_id)
          and not exists(select 1 from direct_sale_prices price where price.sales_slip_item_id=item.id)
      """);
  }

  public static void copyTerms(JdbcTemplate jdbc, Long documentId) {
    jdbc.update(
        """
        insert into direct_sales (sales_slip_id, version, partner_id, sale_date, total_amount,
            expected_payment_date, payment_method, unpaid_payment_label, created_at, updated_at)
        select id, 0, partner_id, sale_date, total_amount, expected_payment_date, payment_method, payment_status,
            created_at, updated_at from sales_slips where id = ? and sales_type = 'DIRECT'
        """,
        documentId);
    jdbc.update(
        """
        insert into direct_sale_prices (sales_slip_item_id, sales_slip_id, priced_quantity, unit_price, amount)
        select id, sales_slip_id, quantity, unit_price, amount from sales_slip_items
        where sales_slip_id = ? and exists (select 1 from direct_sales where sales_slip_id = ?)
        """,
        documentId,
        documentId);
  }
}
