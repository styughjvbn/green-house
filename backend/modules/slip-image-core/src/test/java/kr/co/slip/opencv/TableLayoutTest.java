package kr.co.slip.opencv;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import static org.junit.jupiter.api.Assertions.*;

class TableLayoutTest {
    TableLayoutTest() { NativeOpenCv.load(); }
    private static final double[] RECEIPT = {0, .116, .376, .420, .464, .509, .552, .693, .786, .879, 1};

    @ParameterizedTest @ValueSource(ints = {600, 1000, 1400})
    void receiptUsesMergedHeaderAndFourNarrowGradeCellsAtDifferentSizes(int width) {
        try (MatScope scope = new MatScope()) {
            Rect bounds = new Rect(20, 30, width + 1, 601);
            Mat grid = scope.own(Mat.zeros(700, width + 50, CvType.CV_8UC1));
            List<Integer> xs = Arrays.stream(RECEIPT).mapToObj(x -> (int) Math.round(x * width)).toList();
            List<Integer> ys = List.of(0, 60, 120, 180, 240, 300, 360, 420, 480, 540, 600);
            for (int i = 0; i < xs.size(); i++)
                Imgproc.line(grid, new Point(bounds.x + xs.get(i), bounds.y + (i >= 3 && i <= 5 ? 120 : 0)),
                        new Point(bounds.x + xs.get(i), bounds.y + 600), Scalar.all(255), 2);
            var layout = TableLayout.detect(grid, bounds, xs, ys, PixelScale.IDENTITY);
            assertEquals(TableLayout.Kind.CONSIGNMENT_RECEIPT, layout.kind());
            assertEquals(2, layout.headerRows());
            assertEquals(xs.get(2).intValue(), layout.printedLeft());
            assertEquals(xs.get(6).intValue(), layout.printedRight());
        }
    }

    @Test void receiptCanUseCropCoordinatesWhenItsOuterStrokeIsShifted() {
        try (MatScope scope = new MatScope()) {
            Mat grid = scope.own(Mat.zeros(700, 755, CvType.CV_8UC1));
            List<Integer> xs = List.of(15, 81, 279, 312, 346, 380, 413, 520, 591, 661, 751);
            for (int i = 0; i < xs.size(); i++)
                Imgproc.line(grid, new Point(xs.get(i), i >= 3 && i <= 5 ? 120 : 0),
                        new Point(xs.get(i), 600), Scalar.all(255), 2);
            var layout = TableLayout.detect(grid, new Rect(0, 0, 755, 601), xs,
                    List.of(0, 60, 120, 180, 240, 300), PixelScale.IDENTITY);
            assertEquals(TableLayout.Kind.CONSIGNMENT_RECEIPT, layout.kind());
        }
    }

    @Test void receiptMustHaveAMergedHeaderNotJustMatchingColumnWidths() {
        try (MatScope scope = new MatScope()) {
            Mat grid = scope.own(Mat.zeros(400, 1001, CvType.CV_8UC1));
            List<Integer> xs = Arrays.stream(RECEIPT).mapToObj(x -> (int) Math.round(x * 1000)).toList();
            for (int x : xs) Imgproc.line(grid, new Point(x, 0), new Point(x, 399), Scalar.all(255), 2);
            assertEquals(TableLayout.Kind.UNKNOWN, TableLayout.detect(grid, new Rect(0, 0, 1001, 400), xs,
                    List.of(0, 80, 160, 240), PixelScale.IDENTITY).kind());
        }
    }

    @Test void listingUsesItsSevenColumnsAndNoPrintedChoiceArea() {
        try (MatScope scope = new MatScope()) {
            Mat grid = scope.own(Mat.zeros(400, 1001, CvType.CV_8UC1));
            var layout = TableLayout.detect(grid, new Rect(0, 0, 1001, 400),
                    List.of(0, 132, 333, 450, 566, 683, 819, 1000), List.of(0, 80, 160, 240), PixelScale.IDENTITY);
            assertEquals(TableLayout.Kind.LISTING_REQUEST, layout.kind());
            assertEquals(0, layout.printedRight());
        }
    }

    @Test void normalizesListingColumnsAgainstGridEdgesRatherThanCropPadding() {
        try (MatScope scope = new MatScope()) {
            Mat grid = scope.own(Mat.zeros(400, 672, CvType.CV_8UC1));
            assertEquals(TableLayout.Kind.LISTING_REQUEST, TableLayout.detect(grid, new Rect(0, 0, 672, 400),
                    List.of(6, 88, 215, 289, 363, 437, 522, 635),
                    List.of(0, 80, 160, 240), PixelScale.IDENTITY).kind());
        }
    }

    @Test void receiptColumnCountAloneDoesNotIdentifyAnUnrelatedForm() {
        try (MatScope scope = new MatScope()) {
            Mat grid = scope.own(Mat.zeros(400, 1001, CvType.CV_8UC1));
            assertEquals(TableLayout.Kind.UNKNOWN, TableLayout.detect(grid, new Rect(0, 0, 1001, 400),
                    List.of(0, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000),
                    List.of(0, 80, 160, 240), PixelScale.IDENTITY).kind());
        }
    }
}
