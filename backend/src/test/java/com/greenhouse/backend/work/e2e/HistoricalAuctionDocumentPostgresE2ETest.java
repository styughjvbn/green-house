package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class HistoricalAuctionDocumentPostgresE2ETest extends WorkE2ETestBase {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private BusinessPartnerRepository partners;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void importedDocumentsUseOrdinaryCancellationRulesWithoutRestoringUnknownInventory(
      boolean resultRecorded) throws Exception {
    var partner =
        partners.saveAndFlush(
            new BusinessPartner("이관 전표 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null));
    long shipment =
        jdbc.queryForObject(
            """
        INSERT INTO auction_shipments (id,created_at,updated_at,shipment_date,status,auction_house_id)
        VALUES (nextval('auction_shipments_id_seq'),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,DATE '2025-05-04','WAITING',?) RETURNING id
        """,
            Long.class,
            partner.getId());
    long lot =
        jdbc.queryForObject(
            """
        INSERT INTO auction_shipment_lots (id,created_at,updated_at,shipment_id,item_name,variety_name,
          shipped_quantity,sold_quantity,returned_quantity,waiting_quantity,current_status)
        VALUES (nextval('auction_shipment_lots_id_seq'),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,'난','품종',10,0,0,10,'WAITING') RETURNING id
        """,
            Long.class,
            shipment);
    long slip =
        jdbc.queryForObject(
            """
        INSERT INTO sales_slips (id,created_at,updated_at,sale_date,sales_type,auction_shipment_id,
          partner_id,total_amount,paid_amount,remaining_amount,payment_status,sales_status)
        VALUES (nextval('sales_slips_id_seq'),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,DATE '2025-05-04','AUCTION',?, ?,0,0,0,'정산 대기','출하 완료') RETURNING id
        """,
            Long.class,
            shipment,
            partner.getId());
    jdbc.update(
        """
        INSERT INTO sales_slip_items (id,sales_slip_id,auction_shipment_lot_id,item_name,quantity,unit_price,amount)
        VALUES (nextval('sales_slip_items_id_seq'),?,?,'난',10,0,0)
        """,
        slip,
        lot);
    if (resultRecorded) {
      var result =
          post(
              "/api/auction-lots/" + lot + "/results",
              """
          {"idempotencyKey":"import-result","auctionDate":"2025-05-05","attemptNo":1,
           "attemptStatus":"SOLD","resultLines":[{"quantity":10,"unitPrice":1000}]}
          """);
      assertThat(result.status()).isEqualTo(200);
    }
    var shipmentsBefore = jdbc.queryForList("SELECT * FROM auction_shipments ORDER BY id");
    var lotsBefore = jdbc.queryForList("SELECT * FROM auction_shipment_lots ORDER BY id");
    var movementsBefore = jdbc.queryForList("SELECT * FROM sales_inventory_movements ORDER BY id");
    var groupsBefore = jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id");
    var mutationsBefore = jdbc.queryForList("SELECT * FROM orchid_group_mutations ORDER BY id");
    var detail = get("/api/sales-slips/" + slip);
    assertThat(detail.status()).isEqualTo(200);
    assertThat(detail.data().has("historicalAuctionImport")).isFalse();
    assertThat(detail.data().path("availableActions").toString())
        .isEqualTo(resultRecorded ? "[]" : "[\"CANCEL\"]");
    assertThat(detail.data().path("items").get(0).path("allocations")).isEmpty();
    assertThat(get("/api/sales-slips/" + slip + "/print").data()).isEqualTo(detail.data());
    var edit =
        putJson(
            "/api/sales-slips/" + slip,
            "{\"saleDate\":\"2025-05-04\",\"salesType\":\"DIRECT\",\"partnerId\":"
                + partner.getId()
                + ",\"items\":[]}");
    assertThat(edit.status()).isEqualTo(400);
    var page = get("/api/sales-slips/page?partnerId=" + partner.getId());
    assertThat(page.status()).isEqualTo(200);
    assertThat(page.data().path("content").get(0).has("historicalAuctionImport")).isFalse();
    for (var status : new String[] {"작성중", "출고 완료"}) {
      var response =
          patchJson(
              "/api/sales-slips/" + slip + "/sales-status", "{\"salesStatus\":\"" + status + "\"}");
      assertThat(response.status()).isEqualTo(400);
    }
    var cancellation =
        patchJson("/api/sales-slips/" + slip + "/sales-status", "{\"salesStatus\":\"취소\"}");
    assertThat(cancellation.status()).isEqualTo(resultRecorded ? 400 : 200);
    if (!resultRecorded) {
      assertThat(cancellation.data().path("salesStatus").asText()).isEqualTo("취소");
      assertThat(cancellation.data().path("auctionShipmentId").isNull()).isTrue();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM auction_shipments WHERE id=?", Integer.class, shipment))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM auction_shipment_lots WHERE id=?", Integer.class, lot))
          .isZero();
    } else {
      assertThat(jdbc.queryForList("SELECT * FROM auction_shipments ORDER BY id"))
          .isEqualTo(shipmentsBefore);
      assertThat(jdbc.queryForList("SELECT * FROM auction_shipment_lots ORDER BY id"))
          .isEqualTo(lotsBefore);
    }
    assertThat(jdbc.queryForList("SELECT * FROM sales_inventory_movements ORDER BY id"))
        .isEqualTo(movementsBefore);
    assertThat(jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id"))
        .isEqualTo(groupsBefore);
    assertThat(jdbc.queryForList("SELECT * FROM orchid_group_mutations ORDER BY id"))
        .isEqualTo(mutationsBefore);
  }
}
