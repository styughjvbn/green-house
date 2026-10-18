package kr.co.slip.opencv;

import kr.co.slip.api.OutputMode;
import org.junit.jupiter.api.Test;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import static org.junit.jupiter.api.Assertions.*;

class ImageEncodingTest {
    ImageEncodingTest() { NativeOpenCv.load(); }

    @Test void productionFlattensTransparentPixelsToWhiteWithoutMutatingAnalysisImage() {
        try (MatScope scope = new MatScope()) {
            Mat original = scope.own(new Mat(80, 120, CvType.CV_8UC4, new Scalar(0, 0, 0, 0)));
            scope.own(scope.own(original.submat(new Rect(40, 0, 80, 80))).setTo(new Scalar(30, 90, 150, 255)));
            byte[] jpeg = ImageEncoding.encode(original, OutputMode.PRODUCTION);
            assertEquals(0xff, jpeg[0] & 255);
            assertEquals(0xd8, jpeg[1] & 255);
            Mat decoded = scope.own(Imgcodecs.imdecode(scope.own(new MatOfByte(jpeg)), Imgcodecs.IMREAD_UNCHANGED));
            assertEquals(3, decoded.channels());
            for (double value : decoded.get(20, 20)) assertTrue(value >= 250);
            assertArrayEquals(new double[]{30, 90, 150}, decoded.get(20, 80), 3);
            assertArrayEquals(new double[]{0, 0, 0, 0}, original.get(20, 20));
            assertArrayEquals(new double[]{30, 90, 150, 255}, original.get(20, 80));
            Mat diagnostic = scope.own(Imgcodecs.imdecode(scope.own(new MatOfByte(ImageEncoding.encode(original, OutputMode.ALL))), Imgcodecs.IMREAD_UNCHANGED));
            assertEquals(4, diagnostic.channels());
            assertEquals(0, diagnostic.get(20, 20)[3]);
        }
    }
}
