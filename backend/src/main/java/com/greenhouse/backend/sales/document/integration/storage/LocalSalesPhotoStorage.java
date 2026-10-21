package com.greenhouse.backend.sales.document.integration.storage;

import com.greenhouse.backend.sales.document.application.PhotoStorageException;
import com.greenhouse.backend.sales.document.application.SalesPhotoIntegrity;
import com.greenhouse.backend.sales.document.application.port.out.SalesPhotoStorage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "sales.document.photos.storage.type",
    havingValue = "local",
    matchIfMissing = true)
public class LocalSalesPhotoStorage implements SalesPhotoStorage {
  private final Path root;

  public LocalSalesPhotoStorage(
      @Value("${sales.document.photos.storage.root:./data/slip-photos}") String root) {
    try {
      this.root = Files.createDirectories(Path.of(root).toAbsolutePath().normalize()).toRealPath();
    } catch (IOException e) {
      throw new PhotoStorageException("전표 사진 저장 경로를 준비하지 못했습니다.", e);
    }
  }

  @Override
  public StoredObject store(byte[] bytes, String contentType) {
    if (bytes == null || bytes.length < 1 || bytes.length > 8 * 1024 * 1024)
      throw new IllegalArgumentException("잘못된 사진 크기입니다.");
    String extension =
        switch (contentType) {
          case "image/webp" -> "webp";
          case "image/jpeg" -> "jpg";
          case "image/png" -> "png";
          default -> throw new IllegalArgumentException("지원하지 않는 사진 형식입니다.");
        };
    String id = UUID.randomUUID().toString();
    String key = id.substring(0, 2) + "/" + id + "." + extension;
    Path temporary = null;
    try {
      Path destination = resolve(key);
      Files.createDirectories(destination.getParent());
      if (Files.isSymbolicLink(destination.getParent()))
        throw new IOException("Symbolic storage directory");
      temporary = Files.createTempFile(destination.getParent(), ".upload-", ".tmp");
      try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) output.write(buffer);
        output.force(true);
      }
      Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
      temporary = null;
      // Persist both the rename and the shard entry before committing any DB reference.
      try (var directory = FileChannel.open(destination.getParent(), StandardOpenOption.READ)) {
        directory.force(true);
      }
      try (var directory = FileChannel.open(root, StandardOpenOption.READ)) {
        directory.force(true);
      }
      return new StoredObject(key, bytes.length, SalesPhotoIntegrity.hash(bytes));
    } catch (IOException e) {
      throw new PhotoStorageException("전표 사진을 저장하지 못했습니다.", e);
    } finally {
      if (temporary != null)
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
        }
    }
  }

  @Override
  public byte[] read(String key) {
    try {
      Path file = resolve(key);
      if (Files.isSymbolicLink(file) || Files.isSymbolicLink(file.getParent()))
        throw new IOException("Symbolic storage object");
      long size = Files.size(file);
      if (size < 1 || size > 8L * 1024 * 1024) throw new IOException("Invalid stored photo size");
      return Files.readAllBytes(file);
    } catch (IOException e) {
      throw new PhotoStorageException("저장된 전표 사진을 읽지 못했습니다.", e);
    }
  }

  @Override
  public void delete(String key) {
    try {
      Path file = resolve(key);
      if (Files.isSymbolicLink(file.getParent()))
        throw new IOException("Symbolic storage directory");
      Files.deleteIfExists(file);
    } catch (IOException e) {
      throw new PhotoStorageException("전표 사진 파일을 정리하지 못했습니다.", e);
    }
  }

  @Override
  public ObjectPage list(String cursor, int limit) {
    if (limit < 1 || limit > 1000) throw new IllegalArgumentException("잘못된 조회 개수입니다.");
    // UUID shards keep each directory independent; never follow symbolic links.
    try (var files = Files.walk(root, 2)) {
      var keys =
          files
              .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
              .map(path -> root.relativize(path).toString().replace('\\', '/'))
              .filter(key -> validKey(key) && (cursor == null || key.compareTo(cursor) > 0))
              .sorted()
              .limit(limit + 1L)
              .toList();
      var values = new ArrayList<ObjectInfo>();
      for (String key : keys.subList(0, Math.min(limit, keys.size())))
        values.add(
            new ObjectInfo(
                key,
                Files.getLastModifiedTime(resolve(key), LinkOption.NOFOLLOW_LINKS).toInstant()));
      return new ObjectPage(List.copyOf(values), keys.size() > limit ? keys.get(limit - 1) : null);
    } catch (IOException e) {
      throw new PhotoStorageException("전표 사진 목록을 읽지 못했습니다.", e);
    }
  }

  private Path resolve(String key) {
    if (!validKey(key)) throw new IllegalArgumentException("잘못된 사진 저장 식별자입니다.");
    return root.resolve(key);
  }

  private static boolean validKey(String key) {
    return key != null
        && key.matches(
            "[0-9a-f]{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(webp|jpg|png)");
  }
}
