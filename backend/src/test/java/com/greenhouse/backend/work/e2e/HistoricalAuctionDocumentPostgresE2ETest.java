package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class HistoricalAuctionDocumentPostgresE2ETest extends WorkE2ETestBase {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private BusinessPartnerRepository partners;

  @Test
  void readsAndPrintsImportedDocumentButRejectsCancellationBeforeAnyFarmOrAuctionChange()
      throws Exception {
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
        INSERT INTO sales_slips (id,created_at,updated_at,slip_number,sale_date,sales_type,auction_shipment_id,
          partner_id,total_amount,paid_amount,remaining_amount,payment_status,sales_status,historical_auction_import)
        VALUES (nextval('sales_slips_id_seq'),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,DATE '2025-05-04','AUCTION',?, ?,0,0,0,'정산 대기','출하 완료',true) RETURNING id
        """,
            Long.class,
            "HIST-AUC-" + shipment,
            shipment,
            partner.getId());
    jdbc.update(
        """
        INSERT INTO sales_slip_items (id,sales_slip_id,auction_shipment_lot_id,item_name,quantity,unit_price,amount)
        VALUES (nextval('sales_slip_items_id_seq'),?,?,'난',10,0,0)
        """,
        slip,
        lot);
    var shipmentsBefore = jdbc.queryForList("SELECT * FROM auction_shipments ORDER BY id");
    var lotsBefore = jdbc.queryForList("SELECT * FROM auction_shipment_lots ORDER BY id");
    var movementsBefore = jdbc.queryForList("SELECT * FROM sales_inventory_movements ORDER BY id");
    var groupsBefore = jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id");
    var mutationsBefore = jdbc.queryForList("SELECT * FROM orchid_group_mutations ORDER BY id");
    var detail = get("/api/sales-slips/" + slip);
    assertThat(detail.status()).isEqualTo(200);
    assertThat(detail.data().path("historicalAuctionImport").asBoolean()).isTrue();
    assertThat(detail.data().path("availableActions")).isEmpty();
    assertThat(detail.data().path("items").get(0).path("allocations")).isEmpty();
    assertThat(get("/api/sales-slips/" + slip + "/print").data()).isEqualTo(detail.data());
    var edit =
        putJson(
            "/api/sales-slips/" + slip,
            "{\"saleDate\":\"2025-05-04\",\"salesType\":\"DIRECT\",\"partnerId\":"
                + partner.getId()
                + ",\"items\":[]}");
    assertThat(edit.status()).isEqualTo(409);
    assertThat(edit.body().path("error").path("code").asText())
        .isEqualTo("HISTORICAL_AUCTION_DOCUMENT_READ_ONLY");
    var page = get("/api/sales-slips/page?partnerId=" + partner.getId());
    assertThat(page.status()).isEqualTo(200);
    assertThat(page.data().path("content").get(0).path("historicalAuctionImport").asBoolean())
        .isTrue();
    for (var status : new String[] {"취소", "작성중", "출고 완료"}) {
      var response =
          patchJson(
              "/api/sales-slips/" + slip + "/sales-status", "{\"salesStatus\":\"" + status + "\"}");
      assertThat(response.status()).isEqualTo(409);
      assertThat(response.body().path("error").path("code").asText())
          .isEqualTo("HISTORICAL_AUCTION_DOCUMENT_READ_ONLY");
    }
    assertThat(jdbc.queryForList("SELECT * FROM auction_shipments ORDER BY id"))
        .isEqualTo(shipmentsBefore);
    assertThat(jdbc.queryForList("SELECT * FROM auction_shipment_lots ORDER BY id"))
        .isEqualTo(lotsBefore);
    assertThat(jdbc.queryForList("SELECT * FROM sales_inventory_movements ORDER BY id"))
        .isEqualTo(movementsBefore);
    assertThat(jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id"))
        .isEqualTo(groupsBefore);
    assertThat(jdbc.queryForList("SELECT * FROM orchid_group_mutations ORDER BY id"))
        .isEqualTo(mutationsBefore);
  }
}
