package com.greenhouse.backend.sales.document.application.port.out;

import java.time.Instant;
import java.util.List;

/** Immutable, opaque object keys. Implementations own paths, credentials and transport. */
public interface SalesPhotoStorage {
  StoredObject store(byte[] bytes, String contentType);

  byte[] read(String key);

  void delete(String key);

  /** Bounded listing for offline maintenance; cursor is opaque to callers. */
  ObjectPage list(String cursor, int limit);

  record StoredObject(String key, long byteSize, String sha256) {}

  record ObjectInfo(String key, Instant modifiedAt) {}

  record ObjectPage(List<ObjectInfo> objects, String nextCursor) {}
}
