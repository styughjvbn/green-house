package kr.co.slip.opencv;

import java.util.List;
import kr.co.slip.api.ProcessingResult.Row;
import org.junit.jupiter.api.Test;
import org.opencv.core.*;
import static org.junit.jupiter.api.Assertions.*;

class RowCompactorTest {
    RowCompactorTest() { NativeOpenCv.load(); }

    @Test void noCutoffPreservesTheCompleteImageIncludingBlankRowsAndFooter() {
        try (MatScope scope = new MatScope()) {
            Mat original = image(scope);
            Mat kept = RowCompactor.compact(original, 200,
                    List.of(new Row(0, 240, 260, 0, 0, false, BlankRowCutoff.EMPTY)), scope);
            assertEquals(100, kept.rows());
            assertEquals(0, Core.norm(original, kept, Core.NORM_INF));
        }
    }

    @Test void cutoffPreservesHeaderTransparencyAndPixelsWithoutCompressingEarlierBlankRows() {
        try (MatScope scope = new MatScope()) {
            Mat original = image(scope);
            List<Row> rows = List.of(new Row(0, 230, 240, 0, 0, false, BlankRowCutoff.EMPTY),
                    new Row(3, 257, 280, 0, 0, true, BlankRowCutoff.CUTOFF));
            Mat document = RowCompactor.compact(original, 200, rows, scope);
            Mat table = RowCompactor.compact(scope.own(original.submat(new Rect(5, 20, 10, 60))), 220, rows, scope);
            assertEquals(57, document.rows());
            assertEquals(37, table.rows());
            for (int y = 0; y < document.rows(); y++) assertArrayEquals(original.get(y, 5), document.get(y, 5));
            for (int y = 0; y < table.rows(); y++) assertArrayEquals(table.get(y, 0), document.get(y + 20, 5));
            assertEquals(99, original.get(99, 5)[0]);
            assertNull(RowCompactor.compact(original, 300, rows, scope));
        }
    }

    private static Mat image(MatScope scope) {
        Mat image = scope.own(new Mat(100, 20, CvType.CV_8UC4));
        for (int y = 0; y < image.rows(); y++) scope.own(scope.own(image.row(y)).setTo(new Scalar(y, 10, 20, y < 10 ? 0 : 255)));
        return image;
    }
}
