package com.greenhouse.backend.sales.document.domain;

import static org.assertj.core.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SalesSlipPhotoTest {
  @Test
  void acceptsARealPngAndRejectsSpoofedMimeOrNonImages() throws Exception {
    var buffer = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", buffer);
    String base64 = Base64.getEncoder().encodeToString(buffer.toByteArray());
    assertThat(SalesSlipPhoto.validate("slip.png", "image/png", base64))
        .isEqualTo(buffer.toByteArray());
    assertThatThrownBy(() -> SalesSlipPhoto.validate("slip.jpg", "image/jpeg", base64))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                SalesSlipPhoto.validate(
                    "slip.png",
                    "image/png",
                    Base64.getEncoder().encodeToString("not an image".getBytes())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsLossyAndLosslessWebpAndRejectsMimeSpoofing() {
    for (String base64 :
        List.of(
            "UklGRkIAAABXRUJQVlA4IDYAAAAwAgCdASoCAAIAAIAOJaACdLoB+AH4AARoAAD+++F7/774Zb3b/0Jf/ue7+7Av7nu/+5nAAAA=",
            "UklGRhwAAABXRUJQVlA4TA8AAAAvAUAAAAcQ/Y/+ByKi/wEA")) {
      assertThat(SalesSlipPhoto.validate("slip.webp", "image/webp", base64))
          .isEqualTo(Base64.getDecoder().decode(base64));
      assertThatThrownBy(() -> SalesSlipPhoto.validate("slip.png", "image/png", base64))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void rejectsMissingOversizedAndInvalidBase64Inputs() {
    assertThatThrownBy(() -> SalesSlipPhoto.validate("slip.png", "image/png", "!invalid"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SalesSlipPhoto.validate("slip.png", "image/png", "A".repeat(11184813)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SalesSlipPhoto.validate("slip.png", "image/png", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
