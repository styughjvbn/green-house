package com.greenhouse.backend.common.image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public final class UploadImage {
  public static final int MAX_BYTES = 8 * 1024 * 1024;

  private UploadImage() {}

  public static byte[] validate(String contentType, byte[] bytes) {
    if (bytes.length == 0
        || bytes.length > MAX_BYTES
        || !("image/jpeg".equals(contentType)
            || "image/png".equals(contentType)
            || "image/webp".equals(contentType)))
      throw new IllegalArgumentException("전표 사진은 JPEG, PNG 또는 WebP, 최대 8MB까지 첨부할 수 있습니다.");
    try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw new IllegalArgumentException("읽을 수 없는 사진입니다.");
      var reader = readers.next();
      try {
        reader.setInput(stream);
        String expected =
            switch (contentType) {
              case "image/png" -> "png";
              case "image/webp" -> "webp";
              default -> "JPEG";
            };
        if (!reader.getFormatName().equalsIgnoreCase(expected))
          throw new IllegalArgumentException("사진 형식이 일치하지 않습니다.");
        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
        if (pixels <= 0 || pixels > 25000000)
          throw new IllegalArgumentException("사진 해상도는 2,500만 화소 이하로 첨부해주세요.");
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("읽을 수 없는 사진입니다.");
    }
    return bytes;
  }
}
