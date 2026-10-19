package kr.co.slip.opencv;

import kr.co.slip.api.ProcessingResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.util.*;
import static kr.co.slip.api.ProcessingResult.*;
import static org.junit.jupiter.api.Assertions.*;

class PaperSeparatorTest {
    PaperSeparatorTest() { NativeOpenCv.load(); }
    private static final Scalar DESK = new Scalar(70, 110, 150), PAPER = new Scalar(230, 225, 215);

    @Test void knownDocumentQuadIsExportedWithOpaqueAlpha() {
        Mat image = new Mat(600, 400, CvType.CV_8UC3, PAPER);
        try {
            var result = PaperSeparator.separate(image, List.of(), true);
            assertEquals(1, result.papers().size());
            assertFalse(result.papers().getFirst().reviewRequired());
            MatOfByte encoded = new MatOfByte(result.images().get("paper-01.png"));
            Mat decoded = Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_UNCHANGED);
            try { assertEquals(4, decoded.channels()); assertEquals(255, decoded.get(10, 10)[3]); }
            finally { encoded.release(); decoded.release(); }
        } finally { image.release(); }
    }

    @Test void twoBlankRowsKeepTheFirstAndCutTheDocumentFromTheSecond() {
        Mat image = new Mat(600, 400, CvType.CV_8UC3, PAPER);
        var table = new Table(1, new Box(40, 150, 300, 300), List.of(), List.of(),
                List.of(new Row(1, 200, 250, 0, 0, false, BlankRowCutoff.EMPTY),
                        new Row(2, 250, 300, 0, 0, false, BlankRowCutoff.EMPTY),
                        new Row(4, 350, 450, .1, 20, false, "CONTENT_PRESENT")), 0);
        try {
            Imgproc.rectangle(image, new Point(80, 30), new Point(120, 60), new Scalar(0, 0, 255), -1);
            Imgproc.rectangle(image, new Point(80, 220), new Point(120, 230), new Scalar(0, 255, 0), -1);
            var result = separateAndCompact(image, List.of(table), true);
            try (MatScope scope = new MatScope()) {
                MatOfByte bytes = scope.own(new MatOfByte(result.images().get("paper-01-compacted.png")));
                Mat compact = scope.own(Imgcodecs.imdecode(bytes, Imgcodecs.IMREAD_UNCHANGED));
                assertEquals(250, compact.rows());
                assertEquals(400, compact.cols());
                assertEquals(4, compact.channels());
                assertArrayEquals(new double[] {0, 0, 255, 255}, compact.get(40, 100));
                assertArrayEquals(new double[] {0, 255, 0, 255}, compact.get(225, 100));
                assertEquals(List.of(1), result.papers().getFirst().tableIndices());
                assertTrue(result.images().containsKey("paper-01.png"));
            }
        } finally { image.release(); }
    }

    @Test void isolatedBlankRowsAcrossTablesPreserveInterTableContentAndFooter() {
        Mat image = new Mat(600, 400, CvType.CV_8UC3, PAPER);
        var upper = new Table(1, new Box(40, 100, 300, 100), List.of(), List.of(),
                List.of(new Row(1, 130, 150, 0, 0, false, BlankRowCutoff.EMPTY)), 0);
        var lower = new Table(2, new Box(40, 300, 300, 200), List.of(), List.of(),
                List.of(new Row(1, 350, 400, 0, 0, false, BlankRowCutoff.EMPTY)), 0);
        try {
            Imgproc.rectangle(image, new Point(80, 240), new Point(120, 260), new Scalar(0, 0, 255), -1);
            var result = separateAndCompact(image, List.of(upper, lower), true);
            try (MatScope scope = new MatScope()) {
                MatOfByte bytes = scope.own(new MatOfByte(result.images().get("paper-01-compacted.png")));
                Mat compact = scope.own(Imgcodecs.imdecode(bytes, Imgcodecs.IMREAD_UNCHANGED));
                assertEquals(600, compact.rows());
                assertArrayEquals(new double[] {0, 0, 255, 255}, compact.get(240, 100));
            }
        } finally { image.release(); }
    }

    @Test void noTableDoesNotInventATrimBoundary() {
        Mat image = new Mat(600, 400, CvType.CV_8UC3, PAPER);
        try {
            var result = PaperSeparator.separate(image, List.of(), true);
            assertFalse(result.images().containsKey("paper-01-compacted.png"));
            assertTrue(result.images().containsKey("paper-01.png"));
        } finally { image.release(); }
    }

    @Test void sideBySideOrClippedTablesKeepOriginalPaperWithWarning() {
        Mat image = new Mat(600, 400, CvType.CV_8UC3, PAPER);
        try {
            for (List<Table> tables : List.of(
                    List.of(table(1, 10, 100, 150, 200), table(2, 200, 100, 150, 200)),
                    List.of(table(1, -10, 100, 300, 200)))) {
                var result = separateAndCompact(image, tables, true);
                assertFalse(result.images().containsKey("paper-01-compacted.png"));
                assertTrue(result.images().containsKey("paper-01.png"));
                assertTrue(result.warnings().stream().anyMatch(w -> w.contains("축약을 생략")));
            }
        } finally { image.release(); }
    }

    @Test void trapezoidalPaperIsSeparatedFromDeskWithTransparentCorners() {
        Mat image = new Mat(1000, 800, CvType.CV_8UC3, DESK);
        MatOfPoint polygon = new MatOfPoint(new Point(140, 100), new Point(700, 130), new Point(680, 900), new Point(90, 860));
        try {
            Imgproc.fillConvexPoly(image, polygon, PAPER);
            var result = PaperSeparator.separate(image, List.of(table(1, 200, 280, 400, 450)), false);
            assertEquals(1, result.papers().size());
            var box = result.papers().getFirst().bounds();
            assertEquals(90, box.x(), 20); assertEquals(100, box.y(), 20);
            MatOfByte encoded = new MatOfByte(result.images().get("paper-01.png"));
            Mat decoded = Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_UNCHANGED);
            try {
                assertEquals(4, decoded.channels());
                assertEquals(0, decoded.get(0, 0)[3]);
                assertEquals(255, decoded.get(500 - box.y(), 400 - box.x())[3]);
            } finally { encoded.release(); decoded.release(); }
        } finally { image.release(); polygon.release(); }
    }

    @Test void overlappingPapersAreSplitByHeaderAllowance() {
        Mat image = new Mat(1000, 800, CvType.CV_8UC3, DESK);
        try {
            Imgproc.rectangle(image, new Point(100, 70), new Point(700, 830), PAPER, -1);
            Imgproc.rectangle(image, new Point(120, 430), new Point(720, 930), PAPER, -1);
            Imgproc.line(image, new Point(120, 430), new Point(720, 430), new Scalar(150, 155, 150), 3);
            var result = PaperSeparator.separate(image, List.of(table(1, 180, 260, 430, 140), table(2, 200, 630, 430, 240)), false);
            assertEquals(2, result.papers().size());
            assertEquals("TABLE_SPACING_SPLIT", result.papers().getFirst().method());
            assertEquals(List.of(1), result.papers().getFirst().tableIndices());
            assertEquals(List.of(2), result.papers().getLast().tableIndices());
            var first = result.papers().getFirst().bounds();
            assertEquals(490, first.y() + first.height(), 2);
        } finally { image.release(); }
    }

    @ParameterizedTest @CsvSource({"200,430", "200,230", "300,350", "400,230"})
    void sufficientTableSpacingSplitsWithoutAnObservedEdge(int lowerLeft, int lowerWidth) {
        Mat image = new Mat(1000, 800, CvType.CV_8UC3, DESK);
        try {
            Imgproc.rectangle(image, new Point(100, 70), new Point(700, 930), PAPER, -1);
            var result = PaperSeparator.separate(image, List.of(table(1, 180, 260, 430, 140), table(2, lowerLeft, 630, lowerWidth, 240)), false);
            assertEquals(2, result.papers().size());
            assertEquals("TABLE_SPACING_SPLIT", result.papers().getFirst().method());
            assertEquals(490, result.papers().getLast().bounds().y(), 2);
        } finally { image.release(); }
    }

    @Test void paperEdgeCanBeVisibleInColorWithAlmostEqualGrayValues() {
        Mat image = new Mat(1000, 800, CvType.CV_8UC3, DESK);
        try {
            Imgproc.rectangle(image, new Point(100, 70), new Point(700, 830), new Scalar(235, 215, 200), -1);
            Imgproc.rectangle(image, new Point(120, 430), new Point(720, 930), new Scalar(211, 211, 211), -1);
            var result = PaperSeparator.separate(image, List.of(table(1, 180, 260, 430, 140), table(2, 200, 630, 430, 240)), false);
            assertEquals(2, result.papers().size());
            assertEquals("TABLE_SPACING_SPLIT", result.papers().getFirst().method());
        } finally { image.release(); }
    }

    @Test void headerAllowanceIsIndependentOfPrintedRulesAndPaperColor() {
        Mat image = new Mat(1000, 900, CvType.CV_8UC3, DESK);
        try {
            Imgproc.rectangle(image, new Point(60, 60), new Point(820, 850), PAPER, -1);
            Imgproc.rectangle(image, new Point(250, 430), new Point(780, 950), new Scalar(235, 215, 200), -1);
            Imgproc.line(image, new Point(60, 630), new Point(820, 630), Scalar.all(70), 3);
            Imgproc.rectangle(image, new Point(350, 590), new Point(380, 620), new Scalar(0, 0, 255), -1);
            var result = PaperSeparator.separate(image,
                    List.of(table(1, 110, 200, 660, 200), table(2, 300, 700, 420, 200)), false);
            assertEquals(2, result.papers().size());
            var lower = result.papers().getLast().bounds();
            assertEquals(560, lower.y(), 2);
            try (MatScope scope = new MatScope()) {
                Mat cutout = scope.own(Imgcodecs.imdecode(scope.own(new MatOfByte(result.images().get("paper-02.png"))), Imgcodecs.IMREAD_UNCHANGED));
                assertArrayEquals(new double[]{0, 0, 255, 255}, cutout.get(600 - lower.y(), 360 - lower.x()));
            }
        } finally { image.release(); }
    }

    @Test void tableSpacingDecidesSeparationIndependentlyOfShadows() {
        Mat image = new Mat(1000, 800, CvType.CV_8UC3, DESK);
        try {
            Imgproc.rectangle(image, new Point(100, 70), new Point(700, 930), PAPER, -1);
            Imgproc.rectangle(image, new Point(300, 520), new Point(650, 650), new Scalar(210, 205, 195), -1);
            var result = PaperSeparator.separate(image,
                    List.of(table(1, 180, 260, 430, 140), table(2, 350, 700, 250, 150)), false);
            assertEquals(2, result.papers().size());
        } finally { image.release(); }
    }

    @Test void visibleEdgeCannotSplitTablesCloserThanConfiguredRowMultiplier() {
        Mat image = new Mat(1000, 800, CvType.CV_8UC3, DESK);
        try {
            Imgproc.rectangle(image, new Point(100, 70), new Point(700, 830), PAPER, -1);
            Imgproc.rectangle(image, new Point(120, 430), new Point(720, 930), PAPER, -1);
            Imgproc.line(image, new Point(120, 430), new Point(720, 430), Scalar.all(50), 3);
            var tables = List.of(table(1, 180, 260, 430, 140), table(2, 200, 500, 430, 240));
            var defaults = PaperSeparator.separate(image, tables, false);
            assertEquals(1, defaults.papers().size());
            assertFalse(defaults.warnings().stream().anyMatch(w -> w.contains("하나로 유지")));
            var configured = PaperSeparator.separate(image, tables, false, PixelScale.IDENTITY,
                    kr.co.slip.api.OutputMode.ALL, null, 5);
            assertEquals(2, configured.papers().size());
        } finally { image.release(); }
    }

    @Test void headerAllowanceIsConfigurableAndClampedBelowTheUpperTable() {
        Mat image = new Mat(1000, 800, CvType.CV_8UC3, DESK);
        try {
            Imgproc.rectangle(image, new Point(100, 70), new Point(700, 930), PAPER, -1);
            var tables = List.of(table(1, 180, 260, 430, 140), table(2, 200, 630, 430, 240));
            var shortHeader = PaperSeparator.separate(image, tables, false, PixelScale.IDENTITY,
                    kr.co.slip.api.OutputMode.ALL, null, 5.5, 5);
            assertEquals(530, shortHeader.papers().getLast().bounds().y());
            var tallHeader = PaperSeparator.separate(image, tables, false, PixelScale.IDENTITY,
                    kr.co.slip.api.OutputMode.ALL, null, 5.5, 20);
            assertEquals(412, tallHeader.papers().getLast().bounds().y());
            assertEquals(List.of(1), tallHeader.papers().getFirst().tableIndices());
            assertEquals(List.of(2), tallHeader.papers().getLast().tableIndices());
        } finally { image.release(); }
    }

    @Test void disconnectedPapersAreExportedIndividually() {
        Mat image = new Mat(900, 1200, CvType.CV_8UC3, DESK);
        try {
            Imgproc.rectangle(image, new Point(80, 70), new Point(530, 830), PAPER, -1);
            Imgproc.rectangle(image, new Point(670, 90), new Point(1120, 830), PAPER, -1);
            var result = PaperSeparator.separate(image, List.of(table(1, 140, 300, 300, 400), table(2, 730, 300, 300, 400)), false);
            assertEquals(2, result.papers().size());
        } finally { image.release(); }
    }

    @Test void uniformFrameDoesNotInventPaperBoundary() {
        Mat image = new Mat(800, 600, CvType.CV_8UC3, Scalar.all(255));
        try { assertTrue(PaperSeparator.separate(image, List.of(), false).papers().isEmpty()); }
        finally { image.release(); }
    }

    @ParameterizedTest @CsvSource({"200,230", "300,350", "400,230"})
    void observedPaperEdgeSplitsTablesWithDifferentWidthsAndLeftOffsets(int lowerLeft, int lowerWidth) {
        Mat image = new Mat(1000, 800, CvType.CV_8UC3, DESK);
        try {
            Imgproc.rectangle(image, new Point(100, 70), new Point(700, 830), PAPER, -1);
            Imgproc.rectangle(image, new Point(120, 430), new Point(720, 930), PAPER, -1);
            Imgproc.line(image, new Point(120, 430), new Point(720, 430), new Scalar(150, 155, 150), 3);
            var result = PaperSeparator.separate(image,
                    List.of(table(1, 180, 260, 430, 140), table(2, lowerLeft, 630, lowerWidth, 140)), false);
            assertEquals(2, result.papers().size());
            assertEquals("TABLE_SPACING_SPLIT", result.papers().getFirst().method());
            assertEquals(List.of(1), result.papers().getFirst().tableIndices());
            assertEquals(List.of(2), result.papers().getLast().tableIndices());
        } finally { image.release(); }
    }

    @Test void shadowInsideAGridDoesNotEraseItsPixels() {
        Mat image = new Mat(1000, 800, CvType.CV_8UC3, DESK);
        MatOfPoint shadow = new MatOfPoint(new Point(90, 500), new Point(340, 750), new Point(90, 750));
        try {
            Imgproc.rectangle(image, new Point(100, 70), new Point(700, 930), PAPER, -1);
            Imgproc.fillConvexPoly(image, shadow, Scalar.all(45));
            var result = PaperSeparator.separate(image, List.of(table(1, 180, 260, 430, 500)), false);
            assertEquals(1, result.papers().size());
            Box crop = result.papers().getFirst().bounds();
            try (MatScope scope = new MatScope()) {
                MatOfByte bytes = scope.own(new MatOfByte(result.images().get("paper-01.png")));
                Mat cutout = scope.own(Imgcodecs.imdecode(bytes, Imgcodecs.IMREAD_UNCHANGED));
                assertArrayEquals(new double[]{45, 45, 45, 255}, cutout.get(700 - crop.y(), 200 - crop.x()));
            }
        } finally { shadow.release(); image.release(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void noBlankRunPreservesFooterWritingInAnyInkColor(boolean blue) {
        Mat image = new Mat(600, 400, CvType.CV_8UC3, PAPER);
        Scalar ink = blue ? new Scalar(180, 60, 20) : Scalar.all(0);
        try {
            Imgproc.line(image, new Point(200, 440), new Point(200, 530), ink, 3);
            var result = separateAndCompact(image, List.of(table(1, 40, 150, 300, 300)), true);
            try (MatScope scope = new MatScope()) {
                MatOfByte bytes = scope.own(new MatOfByte(result.images().get("paper-01-compacted.png")));
                Mat compact = scope.own(Imgcodecs.imdecode(bytes, Imgcodecs.IMREAD_UNCHANGED));
                assertEquals(600, compact.rows());
                assertArrayEquals(new double[]{ink.val[0], ink.val[1], ink.val[2], 255}, compact.get(530, 200));
            }
        } finally { image.release(); }
    }

    @Test void flatSplitPreservesBothTableAssignmentsWhenWidthsDiffer() {
        Mat image = new Mat(1000, 850, CvType.CV_8UC3, DESK);
        try {
            Imgproc.rectangle(image, new Point(150, 60), new Point(600, 650), PAPER, -1);
            Imgproc.rectangle(image, new Point(100, 430), new Point(750, 950), PAPER, -1);
            Imgproc.line(image, new Point(100, 430), new Point(750, 430), new Scalar(150, 155, 150), 3);
            var result = PaperSeparator.separate(image, List.of(table(1, 200, 180, 350, 220), table(2, 200, 630, 450, 240)), false);
            assertEquals(2, result.papers().size());
            var upper = result.papers().getFirst().bounds();
            assertEquals(490, upper.y() + upper.height(), 2);
            assertEquals(List.of(1), result.papers().getFirst().tableIndices());
            assertEquals(List.of(2), result.papers().getLast().tableIndices());
        } finally { image.release(); }
    }

    @Test void separationDoesNotConsumeOrApplyRowDecisions() {
        Mat image = new Mat(600, 400, CvType.CV_8UC3, PAPER);
        try {
            Table geometry = table(1, 40, 150, 300, 300);
            Table withRows = new Table(1, geometry.bounds(), List.of(), List.of(),
                    List.of(new Row(0, 250, 450, 0, 0, true, BlankRowCutoff.CUTOFF)), 1);
            var before = PaperSeparator.separate(image, List.of(geometry), true);
            var after = PaperSeparator.separate(image, List.of(withRows), true);
            assertEquals(before.papers(), after.papers());
            assertEquals(before.images().keySet(), after.images().keySet());
            for (String key : before.images().keySet())
                assertArrayEquals(before.images().get(key), after.images().get(key));
            assertFalse(after.images().containsKey("paper-01-compacted.png"));
        } finally { image.release(); }
    }

    private static PaperSeparator.Result separateAndCompact(Mat image, List<Table> tables, boolean knownQuad) {
        var separated = PaperSeparator.separate(image, tables, knownQuad);
        Map<String, byte[]> images = new LinkedHashMap<>(separated.images());
        List<String> warnings = new ArrayList<>(separated.warnings());
        for (Paper paper : separated.papers()) try (MatScope scope = new MatScope()) {
            MatOfByte bytes = scope.own(new MatOfByte(images.get(String.format("paper-%02d.png", paper.index()))));
            Mat cutout = scope.own(Imgcodecs.imdecode(bytes, Imgcodecs.IMREAD_UNCHANGED));
            Box b = paper.bounds();
            PaperCompactor.compact(cutout, new Rect(b.x(), b.y(), b.width(), b.height()),
                    BlankRowCutoff.apply(tables.stream().filter(t -> paper.tableIndices().contains(t.index())).toList()), paper.index(),
                    images, warnings, scope);
        }
        return new PaperSeparator.Result(separated.papers(), images, warnings);
    }

    private static Table table(int index, int x, int y, int width, int height) {
        return new Table(index, new Box(x, y, width, height),
                java.util.stream.IntStream.rangeClosed(0, height / 20).map(i -> y + i * 20).boxed().toList(),
                List.of(), List.of(), 0);
    }
}
