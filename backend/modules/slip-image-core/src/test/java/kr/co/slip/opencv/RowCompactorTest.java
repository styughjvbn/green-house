package kr.co.slip.opencv;

import java.util.List;
import kr.co.slip.api.ProcessingResult.Row;
import org.junit.jupiter.api.Test;
import org.opencv.core.*;
import static org.junit.jupiter.api.Assertions.*;

class RowCompactorTest {
    RowCompactorTest() { NativeOpenCv.load(); }

    @Test void keepsHeaderAndRowPixelsWithGlobalOffsetsAndDropsFooter() {
        try (MatScope scope = new MatScope()) {
            Mat original = scope.own(new Mat(100, 20, CvType.CV_8UC4));
            for (int y = 0; y < 100; y++) {
                Mat row = scope.own(original.row(y));
                row.setTo(new Scalar(y, 10, 20, y < 10 ? 0 : 255));
            }
            Mat compact = RowCompactor.compact(original, 200, 280,
                    List.of(removed(240, 250), removed(260, 270)), scope);
            assertEquals(60, compact.rows());
            assertEquals(20, compact.cols());
            assertEquals(4, compact.channels());
            for (int y = 0; y < 60; y++) {
                int expected = y < 40 ? y : y < 50 ? y + 10 : y + 20;
                assertArrayEquals(original.get(expected, 5), compact.get(y, 5));
            }
            assertEquals(99, original.get(99, 5)[0]);
        }
    }

    @Test void tableAndPaperUseIdenticalRetainedRows() {
        try (MatScope scope = new MatScope()) {
            Mat paper = scope.own(new Mat(100, 40, CvType.CV_8UC1));
            for (int y = 0; y < paper.rows(); y++) scope.own(paper.row(y)).setTo(Scalar.all(y));
            List<Row> rows = List.of(removed(240, 250), removed(260, 270));
            Mat table = RowCompactor.compact(scope.own(paper.submat(new Rect(5, 20, 30, 60))), 220, 280, rows, scope);
            Mat document = RowCompactor.compact(paper, 200, 280, rows, scope);
            assertEquals(40, table.rows());
            for (int y = 0; y < table.rows(); y++) assertArrayEquals(table.get(y, 0), document.get(y + 20, 5));
        }
    }

    @Test void mergesOverlappingIntervalsAndPreservesGapsBetweenTables() {
        try (MatScope scope = new MatScope()) {
            Mat paper = scope.own(new Mat(100, 5, CvType.CV_8UC1));
            for (int y = 0; y < 100; y++) scope.own(paper.row(y)).setTo(Scalar.all(y));
            Mat compact = RowCompactor.compact(paper, 0, 90,
                    List.of(removed(30, 40), removed(20, 35), removed(70, 80)), scope);
            assertEquals(60, compact.rows());
            assertEquals(19, compact.get(19, 0)[0]);
            assertEquals(40, compact.get(20, 0)[0]);
            assertEquals(69, compact.get(49, 0)[0]);
            assertEquals(80, compact.get(50, 0)[0]);
        }
    }

    private static Row removed(int top, int bottom) { return new Row(0, top, bottom, 0, 0, true, "EMPTY"); }
}
