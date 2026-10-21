package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.*;

import com.greenhouse.backend.farm.variety.domain.Variety;
import com.greenhouse.backend.farm.variety.repository.VarietyRepository;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.document.application.PhotoStorageException;
import com.greenhouse.backend.sales.document.application.SalesPhotoMaintenanceService;
import com.greenhouse.backend.sales.document.application.command.*;
import com.greenhouse.backend.sales.document.application.port.out.SalesPhotoStorage;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Tag("work-e2e")
class SalesSlipPhotoPostgresE2ETest extends WorkE2ETestBase {
  @Autowired BusinessPartnerRepository partners;
  @Autowired VarietyRepository varieties;
  @Autowired JdbcTemplate jdbc;
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean SalesPhotoStorage storage;
  @Autowired SalesPhotoMaintenanceService maintenance;

  @Test
  void photoAndDocumentPersistTogetherReplayOnceAndRejectChangedPhoto() throws Exception {
    Long partnerId = partner();
    var photo =
        new SalesSlipPhotoInput(
            "slip.webp", "image/webp", "UklGRhwAAABXRUJQVlA4TA8AAAAvAUAAAAcQ/Y/+ByKi/wEA");
    var request = request(partnerId, photo);
    Mockito.doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return invocation.callRealMethod();
            })
        .when(storage)
        .store(ArgumentMatchers.any(), ArgumentMatchers.anyString());
    long before = storage.list(null, 1000).objects().size();
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
        .isEqualTo("slip.webp");
    String storageKey =
        jdbc.queryForObject(
            "select storage_key from sales_slip_photos where sales_slip_id=?", String.class, id);
    assertThat(storage.read(storageKey))
        .isEqualTo(Base64.getDecoder().decode(photo.contentBase64()));
    assertThat(storage.list(null, 1000).objects()).hasSize((int) before + 1);
    var changed =
        post(
            "/api/sales-slips",
            objectMapper.writeValueAsString(request(partnerId, png("changed.png"))),
            headers);
    assertThat(changed.status()).isEqualTo(409);
    Mockito.doThrow(new PhotoStorageException("offline"))
        .when(storage)
        .store(ArgumentMatchers.any(), ArgumentMatchers.anyString());
    assertThat(post("/api/sales-slips", objectMapper.writeValueAsString(request), headers).data())
        .isEqualTo(first.data());
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
  void photoFreeRequestWorksAndHasNoAttachment() throws Exception {
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
    long before = storage.list(null, 1000).objects().size();
    jdbc.execute(
        "ALTER TABLE sales_slip_photos ADD CONSTRAINT test_reject_photo CHECK (file_name <> 'reject.png')");
    try {
      var response =
          post(
              "/api/sales-slips",
              objectMapper.writeValueAsString(request(partnerId, png("reject.png"))),
              Map.of("Idempotency-Key", key));
      assertThat(response.status()).isEqualTo(409);
      assertThat(storage.list(null, 1000).objects()).hasSize((int) before);
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

  @Test
  void storageFailureDoesNotCreateAnySaleAndSameRequestCanBeRetried() throws Exception {
    Long partnerId = partner();
    String key = "storage-down-" + UUID.randomUUID();
    String body = objectMapper.writeValueAsString(request(partnerId, png("retry.png")));
    Mockito.doThrow(new PhotoStorageException("offline"))
        .when(storage)
        .store(ArgumentMatchers.any(), ArgumentMatchers.anyString());
    var failed = post("/api/sales-slips", body, Map.of("Idempotency-Key", key));
    assertThat(failed.status()).isEqualTo(503);
    assertThat(failed.body().path("error").path("code").asText())
        .isEqualTo("SALES_PHOTO_STORAGE_UNAVAILABLE");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from sales_slips where partner_id=?", Long.class, partnerId))
        .isZero();
    Mockito.reset(storage);
    assertThat(post("/api/sales-slips", body, Map.of("Idempotency-Key", key)).status())
        .isEqualTo(201);
  }

  @Test
  void verifiesStoredFilesAndReportsMissingObjects() throws Exception {
    var created =
        post(
            "/api/sales-slips",
            objectMapper.writeValueAsString(request(partner(), png("stored.png"))),
            Map.of("Idempotency-Key", "stored-" + UUID.randomUUID()));
    assertThat(created.status()).isEqualTo(201);
    long id = created.data().path("id").asLong();
    assertThat(maintenance.verify()).isPositive();
    assertThat(runVerificationCli()).contains("verified=");
    String object =
        jdbc.queryForObject(
            "select storage_key from sales_slip_photos where sales_slip_id=?", String.class, id);
    storage.delete(object);
    assertThat(get("/api/sales-slips/" + id + "/source-photo").status()).isEqualTo(503);
    jdbc.update("delete from sales_slip_photos where sales_slip_id=?", id);
  }

  @Test
  void concurrentRetriesAttachOnlyOneImmutableFile() throws Exception {
    String key = "photo-concurrent-" + UUID.randomUUID();
    String body = objectMapper.writeValueAsString(request(partner(), png("parallel.png")));
    int before = storage.list(null, 1000).objects().size();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> post("/api/sales-slips", body, Map.of("Idempotency-Key", key)));
      var second =
          pool.submit(() -> post("/api/sales-slips", body, Map.of("Idempotency-Key", key)));
      var a = first.get();
      var b = second.get();
      assertThat(a.status()).isEqualTo(201);
      assertThat(b.status()).isEqualTo(201);
      assertThat(a.data()).isEqualTo(b.data());
    }
    assertThat(storage.list(null, 1000).objects()).hasSize(before + 1);
  }

  @Test
  void schemaRequiresCompleteFileMetadataAndHasNoBinaryColumn() throws Exception {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_schema='public' and table_name='sales_slip_photos' and column_name='content'",
                Long.class))
        .isZero();
    var created =
        post(
            "/api/sales-slips",
            objectMapper.writeValueAsString(request(partner(), png("constraints.png"))),
            Map.of("Idempotency-Key", "constraints-" + UUID.randomUUID()));
    assertThat(created.status()).isEqualTo(201);
    long id = created.data().path("id").asLong();
    for (String change :
        List.of(
            "storage_key=null",
            "storage_key=' '",
            "byte_size=null",
            "byte_size=0",
            "byte_size=8388609",
            "sha256=null",
            "sha256='invalid'",
            "content_type='image/svg+xml'")) {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "update sales_slip_photos set " + change + " where sales_slip_id=?", id))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  private String runVerificationCli() throws Exception {
    Path output = Files.createTempFile("photo-verification-cli-", ".log");
    try {
      var builder =
          new ProcessBuilder(
                  Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                  "-Xmx512m",
                  "-cp",
                  System.getProperty("greenhouse.cli.runtime-classpath"),
                  "com.greenhouse.backend.sales.document.application.SalesPhotoStorageCli",
                  "--photo-storage.command=verify",
                  "--spring.main.banner-mode=off",
                  "--logging.level.root=ERROR",
                  "--logging.level.com.greenhouse.backend=ERROR",
                  "--logging.level.org.hibernate.SQL=OFF",
                  "--app.auth.enabled=false",
                  "--debug=false")
              .redirectErrorStream(true)
              .redirectOutput(output.toFile());
      builder.environment().put("DATABASE_URL", testDatabase.getJdbcUrl());
      builder.environment().put("DATABASE_USERNAME", testDatabase.getUsername());
      builder.environment().put("DATABASE_PASSWORD", testDatabase.getPassword());
      builder
          .environment()
          .put("JPA_DDL_AUTO", "create-drop"); // CLI must override unsafe inherited defaults.
      builder
          .environment()
          .put(
              "SALES_PHOTO_STORAGE_ROOT", ReflectionTestUtils.getField(storage, "root").toString());
      var process = builder.start();
      boolean completed = process.waitFor(90, TimeUnit.SECONDS);
      if (!completed) process.destroyForcibly().waitFor();
      String log = Files.readString(output);
      assertThat(completed).as("Verification CLI timed out: %s", log).isTrue();
      assertThat(process.exitValue()).as("Verification CLI failed: %s", log).isZero();
      return log;
    } finally {
      Files.deleteIfExists(output);
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
    Long varietyId =
        varieties.findAll().stream()
            .filter(variety -> variety.getCode().equals("PHOTO-TEST"))
            .map(Variety::getId)
            .findFirst()
            .orElseGet(
                () ->
                    varieties
                        .saveAndFlush(
                            new Variety(
                                "PHOTO-TEST", "테스트 속", "테스트 품종", null, "2치", true, null, null))
                        .getId());
    return new SalesSlipCommand(
        LocalDate.of(2026, 10, 13),
        SalesType.DIRECT,
        partnerId,
        null,
        "미입금",
        "출고 완료",
        null,
        null,
        List.of(
            new SalesSlipItemInput(
                "테스트 품종", null, null, 2, 1000, null, List.of(), null, null, varietyId)),
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
