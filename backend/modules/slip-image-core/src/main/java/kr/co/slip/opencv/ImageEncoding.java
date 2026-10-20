package kr.co.slip.opencv;

import kr.co.slip.api.OutputMode;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/** Preserve diagnostic masks; flatten production document backgrounds before WebP encoding. */
final class ImageEncoding {
    private ImageEncoding() {}

    static String extension(OutputMode mode) { return mode == OutputMode.PRODUCTION ? ".webp" : ".png"; }

    static byte[] encode(Mat image, OutputMode mode) {
        try (MatScope scope = new MatScope()) {
            Mat output = image;
            if (mode == OutputMode.PRODUCTION && image.channels() == 4) {
                output = scope.mat();
                Imgproc.cvtColor(image, output, Imgproc.COLOR_BGRA2BGR);
                Mat alpha = scope.mat(), invisible = scope.mat();
                Core.extractChannel(image, alpha, 3);
                Core.compare(alpha, Scalar.all(0), invisible, Core.CMP_EQ);
                scope.own(output.setTo(Scalar.all(255), invisible));
            }
            MatOfByte bytes = scope.own(new MatOfByte());
            MatOfInt parameters = scope.own(mode == OutputMode.PRODUCTION
                    ? new MatOfInt(Imgcodecs.IMWRITE_WEBP_QUALITY, 92) : new MatOfInt());
            if (!Imgcodecs.imencode(extension(mode), output, bytes, parameters))
                throw new IllegalStateException("Image encode failed");
            return bytes.toArray();
        }
    }
}
