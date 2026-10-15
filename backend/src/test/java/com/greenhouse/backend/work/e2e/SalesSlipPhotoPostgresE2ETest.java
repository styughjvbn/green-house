package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.*;

import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.document.application.command.*;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class SalesSlipPhotoPostgresE2ETest extends WorkE2ETestBase {
  @Autowired BusinessPartnerRepository partners;
  @Autowired JdbcTemplate jdbc;

  @Test
  void photoAndDocumentPersistTogetherReplayOnceAndRejectChangedPhoto() throws Exception {
    Long partnerId = partner();
    var photo = png("slip.png");
    var request = request(partnerId, photo);
    var headers = Map.of("Idempotency-Key", "photo-" + UUID.randomUUID());
    var first = post("/api/sales-slips", objectMapper.writeValueAsString(request), headers);
    assertThat(first.status()).isEqualTo(201);
    long id = first.data().path("id").asLong();
    var second = post("/api/sales-slips", objectMapper.writeValueAsString(request), headers);
    assertThat(second.status()).isEqualTo(201);
    assertThat(second.data()).isEqualTo(first.data());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from sales_slip_photos where sales_slip_id=?", Long.class, id))
        .isEqualTo(1);
    assertThat(
            get("/api/sales-slips/" + id + "/source-photo").data().path("contentBase64").asText())
        .isEqualTo(photo.contentBase64());
    assertThat(
            get("/api/sales-slips/" + id + "/source-photo/info").data().path("fileName").asText())
        .isEqualTo("slip.png");
    var changed =
        post(
            "/api/sales-slips",
            objectMapper.writeValueAsString(request(partnerId, png("changed.png"))),
            headers);
    assertThat(changed.status()).isEqualTo(409);
  }

  @Test
  void invalidPhotoDoesNotCreateDocumentOrReceipt() throws Exception {
    Long partnerId = partner();
    var photo =
        new SalesSlipPhotoInput(
            "bad.png", "image/png", Base64.getEncoder().encodeToString("bad".getBytes()));
    String key = "bad-photo-" + UUID.randomUUID();
    var response =
        post(
            "/api/sales-slips",
            objectMapper.writeValueAsString(request(partnerId, photo)),
            Map.of("Idempotency-Key", key));
    assertThat(response.status()).isEqualTo(400);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from sales_slips where partner_id=?", Long.class, partnerId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from sales_creation_receipts where request_key=?",
                Long.class,
                key))
        .isZero();
  }

  @Test
  void legacyPhotoFreeRequestStillWorksAndHasNoAttachment() throws Exception {
    var response =
        post(
            "/api/sales-slips",
            objectMapper.writeValueAsString(request(partner(), null)),
            Map.of("Idempotency-Key", "no-photo-" + UUID.randomUUID()));
    assertThat(response.status()).isEqualTo(201);
    assertThat(
            get("/api/sales-slips/" + response.data().path("id").asLong() + "/source-photo/info")
                .data()
                .isNull())
        .isTrue();
  }

  @Test
  void photoPersistenceFailureRollsBackDocumentAndCreationReceipt() throws Exception {
    Long partnerId = partner();
    String key = "photo-storage-failure-" + UUID.randomUUID();
    jdbc.execute(
        "ALTER TABLE sales_slip_photos ADD CONSTRAINT test_reject_photo CHECK (file_name <> 'reject.png')");
    try {
      var response =
          post(
              "/api/sales-slips",
              objectMapper.writeValueAsString(request(partnerId, png("reject.png"))),
              Map.of("Idempotency-Key", key));
      assertThat(response.status()).isEqualTo(409);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from sales_slips where partner_id=?", Long.class, partnerId))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from sales_creation_receipts where request_key=?",
                  Long.class,
                  key))
          .isZero();
    } finally {
      jdbc.execute("ALTER TABLE sales_slip_photos DROP CONSTRAINT test_reject_photo");
    }
  }

  private Long partner() {
    return partners
        .saveAndFlush(
            new BusinessPartner(
                "사진 거래처 " + UUID.randomUUID(), PartnerType.WHOLESALE, null, null, null, null))
        .getId();
  }

  private SalesSlipCommand request(Long partnerId, SalesSlipPhotoInput photo) {
    return new SalesSlipCommand(
        LocalDate.of(2026, 10, 13),
        SalesType.DIRECT,
        partnerId,
        null,
        "미입금",
        "출고 완료",
        null,
        null,
        List.of(new SalesSlipItemInput("테스트 품종", null, null, 2, 1000, null, List.of())),
        null,
        true,
        photo);
  }

  private SalesSlipPhotoInput png(String name) throws Exception {
    var buffer = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", buffer);
    return new SalesSlipPhotoInput(
        name, "image/png", Base64.getEncoder().encodeToString(buffer.toByteArray()));
  }
}
