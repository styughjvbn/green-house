package com.greenhouse.backend.sales.document.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class SalesPhotoIntegrity {
  private SalesPhotoIntegrity() {}

  public static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public static void verify(byte[] bytes, Long size, String hash) {
    if (size == null || bytes.length != size || !hash(bytes).equals(hash))
      throw new PhotoStorageException("전표 사진 파일의 크기 또는 해시가 일치하지 않습니다.");
  }
}
