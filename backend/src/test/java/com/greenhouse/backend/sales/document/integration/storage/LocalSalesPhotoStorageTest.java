package com.greenhouse.backend.sales.document.integration.storage;

import static org.assertj.core.api.Assertions.*;

import com.greenhouse.backend.sales.document.application.SalesPhotoIntegrity;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalSalesPhotoStorageTest {
  @TempDir Path root;

  @Test
  void immutableObjectsRoundTripArePagedAndRejectUnsafePaths() throws Exception {
    var storage = new LocalSalesPhotoStorage(root.toString());
    byte[] bytes = "encoded-photo".getBytes();
    var first = storage.store(bytes, "image/webp");
    assertThat(first.key()).endsWith(".webp");
    assertThat(first.sha256()).isEqualTo(SalesPhotoIntegrity.hash(bytes));
    assertThat(storage.read(first.key())).isEqualTo(bytes);
    var second = storage.store(bytes, "image/webp");
    assertThat(first.key()).isNotEqualTo(second.key());
    var page = storage.list(null, 1);
    assertThat(page.objects()).hasSize(1);
    assertThat(storage.list(page.nextCursor(), 1).objects()).hasSize(1);
    for (String bad : List.of("../secret", "/etc/passwd", "aa/../../file"))
      assertThatThrownBy(() -> storage.read(bad)).isInstanceOf(IllegalArgumentException.class);
    storage.delete(first.key());
    assertThat(storage.list(null, 10).objects()).hasSize(1);
    Files.write(root.resolve(second.key()), "corrupt".getBytes());
    assertThatThrownBy(
            () ->
                SalesPhotoIntegrity.verify(
                    storage.read(second.key()), second.byteSize(), second.sha256()))
        .hasMessageContaining("해시");
  }

  @Test
  void concurrentUploadsPublishCompleteDistinctFiles() throws Exception {
    var storage = new LocalSalesPhotoStorage(root.toString());
    byte[] bytes = new byte[128 * 1024];
    new Random(42).nextBytes(bytes);
    try (var pool = Executors.newFixedThreadPool(4)) {
      var futures = new ArrayList<Future<String>>();
      for (int i = 0; i < 12; i++)
        futures.add(
            pool.submit(
                () -> {
                  var object = storage.store(bytes, "image/webp");
                  assertThat(storage.read(object.key())).isEqualTo(bytes);
                  return object.key();
                }));
      var keys = new HashSet<String>();
      for (var future : futures) keys.add(future.get());
      assertThat(keys).hasSize(12);
    }
    try (var files = Files.walk(root)) {
      assertThat(files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp"))).isTrue();
    }
  }

  @Test
  void refusesSymlinkedStorageDirectories() throws Exception {
    var storage = new LocalSalesPhotoStorage(root.toString());
    var object = storage.store(new byte[] {1}, "image/png");
    Path outside = Files.createDirectory(root.resolve("outside"));
    Files.delete(root.resolve(object.key()));
    Path shard = root.resolve(object.key()).getParent();
    Files.delete(shard);
    Files.createSymbolicLink(shard, outside);
    Files.write(outside.resolve(Path.of(object.key()).getFileName()), new byte[] {2});
    assertThatThrownBy(() -> storage.read(object.key())).hasMessageContaining("읽지");
    assertThatThrownBy(() -> storage.delete(object.key())).hasMessageContaining("정리");
  }
}
