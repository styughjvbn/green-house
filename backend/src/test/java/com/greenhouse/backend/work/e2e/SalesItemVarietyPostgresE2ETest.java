package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.*;

import com.greenhouse.backend.farm.variety.domain.Variety;
import com.greenhouse.backend.farm.variety.repository.VarietyRepository;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class SalesItemVarietyPostgresE2ETest extends WorkE2ETestBase {
  @Autowired VarietyRepository varieties;
  @Autowired BusinessPartnerRepository partners;
  @Autowired JdbcTemplate jdbc;

  @Test
  void identitiesPersistAndReplayWithoutReplacingHistoricalNamesAfterMasterRename()
      throws Exception {
    var variety =
        varieties.save(
            new Variety("VID-" + UUID.randomUUID(), "속 A", "품종 A", null, "2치", false, null, null));
    var partner =
        partners.save(
            new BusinessPartner("품종 연결 거래처", PartnerType.WHOLESALE, null, null, null, null));
    String body =
        """
        {"saleDate":"2026-10-20","salesType":"DIRECT","partnerId":%d,
         "salesStatus":"출고 완료","recordOnly":true,"items":[
          {"itemName":"품종 A","genus":"속 A","varietyId":%d,"quantity":2,"unitPrice":100,"allocations":[]}]}
        """
            .formatted(partner.getId(), variety.getId());
    var headers = Map.of("Idempotency-Key", "variety-id-" + UUID.randomUUID());
    var created = post("/api/sales-slips", body, headers);
    assertThat(created.status()).isEqualTo(201);
    long id = created.data().path("id").asLong();
    assertThat(created.data().path("items").get(0).path("varietyId").asLong())
        .isEqualTo(variety.getId());
    assertThat(
            jdbc.queryForObject(
                "SELECT variety_id FROM sales_slip_items WHERE sales_slip_id=?", Long.class, id))
        .isEqualTo(variety.getId());
    jdbc.update("UPDATE varieties SET name='품종 B',genus='속 B' WHERE id=?", variety.getId());
    var detail = get("/api/sales-slips/" + id).data().path("items").get(0);
    assertThat(detail.path("varietyId").asLong()).isEqualTo(variety.getId());
    assertThat(detail.path("itemName").asText()).isEqualTo("품종 A");
    assertThat(detail.path("genus").asText()).isEqualTo("속 A");
    assertThat(post("/api/sales-slips", body, headers).data()).isEqualTo(created.data());
    assertThat(
            post(
                    "/api/sales-slips",
                    body.replace("\"varietyId\":" + variety.getId(), "\"varietyId\":99999999"),
                    headers)
                .status())
        .isEqualTo(409);
    var invalid =
        post(
            "/api/sales-slips",
            body.replace("\"varietyId\":" + variety.getId(), "\"varietyId\":99999999"),
            Map.of("Idempotency-Key", "invalid-variety-" + UUID.randomUUID()));
    assertThat(invalid.status()).isEqualTo(400);
    for (String missingId :
        new String[] {
          body.replace("\"varietyId\":" + variety.getId() + ",", ""),
          body.replace("\"varietyId\":" + variety.getId(), "\"varietyId\":null")
        }) {
      var rejected =
          post(
              "/api/sales-slips",
              missingId,
              Map.of("Idempotency-Key", "missing-variety-" + UUID.randomUUID()));
      assertThat(rejected.status()).isEqualTo(400);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_slips WHERE partner_id=?", Long.class, partner.getId()))
        .isEqualTo(1L);
  }
}
