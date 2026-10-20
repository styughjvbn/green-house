package com.greenhouse.backend.sales.document.domain;

import com.greenhouse.backend.common.image.UploadImage;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sales_slip_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesSlipPhoto {
  public static final int MAX_BYTES = 8 * 1024 * 1024;
  @Id private Long salesSlipId;

  @Column(nullable = false, length = 255)
  private String fileName;

  @Column(nullable = false, length = 32)
  private String contentType;

  @Column(nullable = false, length = 512, unique = true)
  private String storageKey;

  @Column(nullable = false)
  private Long byteSize;

  @Column(nullable = false, length = 64)
  private String sha256;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  public SalesSlipPhoto(
      Long slipId,
      String fileName,
      String contentType,
      String storageKey,
      long byteSize,
      String sha256,
      LocalDateTime now) {
    this.salesSlipId = slipId;
    this.fileName = fileName;
    this.contentType = contentType;
    this.createdAt = now;
    if (storageKey == null
        || storageKey.isBlank()
        || storageKey.length() > 512
        || byteSize < 1
        || byteSize > MAX_BYTES
        || sha256 == null
        || !sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("잘못된 사진 저장 정보입니다.");
    this.storageKey = storageKey;
    this.byteSize = byteSize;
    this.sha256 = sha256;
  }

  public static byte[] validate(String fileName, String contentType, String contentBase64) {
    if (fileName == null
        || fileName.isBlank()
        || fileName.length() > 255
        || contentBase64 == null
        || contentBase64.length() > 11184812) {
      throw new IllegalArgumentException("전표 사진은 JPEG, PNG 또는 WebP, 최대 8MB까지 첨부할 수 있습니다.");
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(contentBase64);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("사진 데이터가 올바르지 않습니다.");
    }
    UploadImage.validate(contentType, bytes);
    return bytes;
  }
}
