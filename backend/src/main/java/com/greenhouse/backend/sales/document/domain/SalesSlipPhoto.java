package com.greenhouse.backend.sales.document.domain;

import com.greenhouse.backend.common.image.UploadImage;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Arrays;
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

  @Column(nullable = false, columnDefinition = "bytea")
  private byte[] content;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  public SalesSlipPhoto(
      Long slipId, String fileName, String contentType, byte[] content, LocalDateTime now) {
    this.salesSlipId = slipId;
    this.fileName = fileName;
    this.contentType = contentType;
    this.content = Arrays.copyOf(content, content.length);
    this.createdAt = now;
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
