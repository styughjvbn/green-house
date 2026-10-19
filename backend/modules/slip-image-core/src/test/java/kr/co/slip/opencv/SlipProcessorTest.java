package kr.co.slip.opencv;

import kr.co.slip.api.ProcessingResult;

import kr.co.slip.api.ProcessingOptions;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import static org.junit.jupiter.api.Assertions.*;

class SlipProcessorTest {
    private final SlipProcessor processor = new SlipProcessor();

    @Test void rejectsInvalidBytes() {
        assertThrows(IllegalArgumentException.class, () -> processor.process(new byte[]{1, 2}, ProcessingOptions.defaults()));
    }

    @Test void noTableIsPreserved() {
        Mat image = new Mat(1000, 800, CvType.CV_8UC3, Scalar.all(255));
        try {
            var result = processor.process(encode(image), ProcessingOptions.defaults());
            assertTrue(result.report().tables().isEmpty());
            assertFalse(result.report().warnings().isEmpty());
        } finally { image.release(); }
    }

    @ParameterizedTest @ValueSource(ints = {4, 5})
    void tableRequiresAtLeastFiveVerticalBoundaries(int boundaries) {
        Mat image = new Mat(1000, 800, CvType.CV_8UC3, Scalar.all(245));
        try {
            for (int y = 200; y <= 800; y += 60)
                Imgproc.line(image, new Point(100, y), new Point(700, y), Scalar.all(0), 2);
            for (int i = 0; i < boundaries; i++) {
                double x = 100 + i * 600. / (boundaries - 1);
                Imgproc.line(image, new Point(x, 200), new Point(x, 800), Scalar.all(0), 2);
            }
            var result = processor.process(encode(image), ProcessingOptions.defaults()
                    .withRotation(0).withOutputMode(kr.co.slip.api.OutputMode.PRODUCTION));
            assertEquals(boundaries >= 5 ? 1 : 0, result.report().tables().size());
        } finally { image.release(); }
    }

    @Test void threeDarkTableSidesDoNotJustifyCuttingOffAContinuousPaperHeader() {
        Mat image = new Mat(1200, 900, CvType.CV_8UC3, Scalar.all(235));
        try {
            Imgproc.rectangle(image, new Point(0, 240), new Point(74, 1199), Scalar.all(85), -1);
            Imgproc.rectangle(image, new Point(826, 240), new Point(899, 1199), Scalar.all(85), -1);
            Imgproc.rectangle(image, new Point(0, 1156), new Point(899, 1199), Scalar.all(85), -1);
            for (int y = 250; y <= 1150; y += 75)
                Imgproc.line(image, new Point(80, y), new Point(820, y), Scalar.all(0), 2);
            for (int x = 80; x <= 820; x += 148)
                Imgproc.line(image, new Point(x, 250), new Point(x, 1150), Scalar.all(0), 2);
            Imgproc.putText(image, "HEADER", new Point(250, 140), Imgproc.FONT_HERSHEY_SIMPLEX, 1, Scalar.all(0), 2);
            var result = processor.process(encode(image), ProcessingOptions.defaults().withRotation(0));
            assertTrue(result.report().documentCorners().isEmpty());
            assertNotEquals("DOCUMENT_PERSPECTIVE", result.report().correction());
        } finally { image.release(); }
    }

    @Test void receiptIgnoresOnlyThePrintedChoiceColumnsAndKeepsRepeatedBlackWriting() {
        Mat image = new Mat(1400, 1200, CvType.CV_8UC3, Scalar.all(255));
        try {
            for (int y = 200; y <= 1160; y += 80)
                Imgproc.line(image, new Point(100, y), new Point(1100, y), Scalar.all(0), 2);
            double[] columns = {0, .116, .376, .420, .464, .509, .552, .693, .786, .879, 1};
            for (int c = 0; c < columns.length; c++) {
                int start = c >= 3 && c <= 5 ? 360 : 200;
                double x = 100 + columns[c] * 1000;
                Imgproc.line(image, new Point(x, start), new Point(x, 1160), Scalar.all(0), 2);
            }
            for (int row = 2; row < 12; row++) {
                for (int c = 2; c <= 5; c++)
                    Imgproc.putText(image, "X", new Point(107 + columns[c] * 1000, 250 + row * 80),
                            Imgproc.FONT_HERSHEY_SIMPLEX, .55, Scalar.all(0), 2);
                if (row <= 6)
                    Imgproc.putText(image, "123", new Point(260, 250 + row * 80),
                            Imgproc.FONT_HERSHEY_SIMPLEX, .8, Scalar.all(0), 5);
            }
            Imgproc.putText(image, "BLUE", new Point(850, 810), Imgproc.FONT_HERSHEY_SIMPLEX,
                    .8, new Scalar(180, 60, 20), 5);
            // A mark only in the excluded choice cells is deliberately not row evidence.
            Imgproc.circle(image, new Point(540, 960), 12, Scalar.all(0), 2);
            var result = processor.process(encode(image), new ProcessingOptions(2200, 2, .01, 0));
            assertEquals(1, result.report().tables().size());
            var rows = result.report().tables().getFirst().rows();
            assertFalse(result.report().warnings().stream().anyMatch(w -> w.contains("양식을 확인하지 못해")));
            for (int row = 2; row <= 7; row++) assertFalse(rows.get(row).removed(), "Written row " + row);
            assertFalse(rows.get(8).removed(), "Keep the first blank row");
            for (int row = 9; row < rows.size(); row++) assertTrue(rows.get(row).removed(), "Blank/choice-only row " + row);
            assertEquals(0, rows.get(9).inkPixels());
            assertFalse(rows.get(0).removed());
            var defaultRows = processor.process(encode(image), ProcessingOptions.defaults().withRotation(0))
                    .report().tables().getFirst().rows();
            assertTrue(rows.subList(2, 4).stream().anyMatch(row -> row.inkRatio() > .01));
            assertFalse(defaultRows.get(2).removed(), "Keep the first faint blank row");
            assertTrue(defaultRows.get(3).removed());
            assertEquals(BlankRowCutoff.CUTOFF, defaultRows.get(3).reason());
        } finally { image.release(); }
    }

    @Test void inkThresholdIncludesEqualityButNotTheNextHigherRatio() {
        assertEquals(.015, ProcessingOptions.defaults().minimumInkRatio());
        Mat image = new Mat(1400, 1000, CvType.CV_8UC3, Scalar.all(255));
        try {
            for (int y = 200; y <= 1160; y += 80)
                Imgproc.line(image, new Point(100, y), new Point(900, y), Scalar.all(0), 2);
            for (double x : new double[]{0, .132, .333, .450, .566, .683, .819, 1})
                Imgproc.line(image, new Point(100 + x * 800, 200), new Point(100 + x * 800, 1160), Scalar.all(0), 2);
            Imgproc.putText(image, "ITEM", new Point(200, 410), Imgproc.FONT_HERSHEY_SIMPLEX, 1, Scalar.all(0), 3);
            byte[] bytes = encode(image);
            double ratio = processor.process(bytes, ProcessingOptions.defaults().withRotation(0))
                    .report().tables().getFirst().rows().get(2).inkRatio();
            assertTrue(ratio > 0 && ratio < .1);
            var equalRows = processor.process(bytes, new ProcessingOptions(2200, 2, ratio, 0))
                    .report().tables().getFirst().rows();
            assertFalse(equalRows.get(2).removed(), "Keep the first blank at the threshold");
            assertEquals(BlankRowCutoff.EMPTY, equalRows.get(2).reason());
            assertTrue(equalRows.get(3).removed());
            var above = processor.process(bytes, new ProcessingOptions(2200, 2, Math.nextDown(ratio), 0))
                    .report().tables().getFirst().rows().get(2);
            assertFalse(above.removed());
            assertEquals("CONTENT_PRESENT", above.reason());
        } finally { image.release(); }
    }

    @Test void anUnrecognizedTableIsPreservedWithoutGuessingAPrintedTemplate() {
        Mat image = new Mat(1400, 1000, CvType.CV_8UC3, Scalar.all(255));
        try {
            for (int y = 200; y <= 1160; y += 80)
                Imgproc.line(image, new Point(100, y), new Point(900, y), Scalar.all(0), 2);
            for (int x = 100; x <= 900; x += 200)
                Imgproc.line(image, new Point(x, 200), new Point(x, 1160), Scalar.all(0), 2);
            Imgproc.line(image, new Point(450, 650), new Point(650, 910), Scalar.all(0), 2);
            Imgproc.line(image, new Point(650, 650), new Point(450, 910), Scalar.all(0), 2);
            var result = processor.process(encode(image), ProcessingOptions.defaults().withRotation(0));
            assertEquals(0, result.report().tables().getFirst().removedRows());
            assertTrue(result.report().tables().getFirst().rows().stream()
                    .allMatch(row -> row.reason().equals("UNRECOGNIZED_LAYOUT")));
        } finally { image.release(); }
    }

    @Test void perspectivePageHasFourSourceCorners() {
        Mat image = new Mat(1400, 1000, CvType.CV_8UC3, new Scalar(70, 65, 60));
        MatOfPoint polygon = new MatOfPoint(new Point(160, 130), new Point(860, 180), new Point(900, 1250), new Point(80, 1200));
        try {
            Imgproc.fillConvexPoly(image, polygon, Scalar.all(245));
            var result = processor.process(encode(image), ProcessingOptions.defaults());
            assertEquals("DOCUMENT_PERSPECTIVE", result.report().correction());
            assertEquals(4, result.report().documentCorners().size());
        } finally { polygon.release(); image.release(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void lowInkRowsTriggerCutoffEvenBeforeAnEndMark(boolean blue) {
        Mat image = new Mat(1400, 1000, CvType.CV_8UC3, Scalar.all(255));
        try {
            for (int y = 200; y <= 1160; y += 80)
                Imgproc.line(image, new Point(100, y), new Point(900, y), Scalar.all(0), 2);
            for (double x : new double[]{0, .132, .333, .450, .566, .683, .819, 1})
                Imgproc.line(image, new Point(100 + x * 800, 200), new Point(100 + x * 800, 1160), Scalar.all(0), 2);
            Imgproc.putText(image, "ITEM", new Point(150, 410), Imgproc.FONT_HERSHEY_SIMPLEX, .8, Scalar.all(0), 2);
            Imgproc.putText(image, "120", new Point(150, 810), Imgproc.FONT_HERSHEY_SIMPLEX, .8, Scalar.all(0), 2);
            Scalar ink = blue ? new Scalar(180, 60, 20) : Scalar.all(0);
            Imgproc.line(image, new Point(450, 650), new Point(650, 910), ink, 2);
            Imgproc.line(image, new Point(650, 650), new Point(450, 910), ink, 2);
            var result = processor.process(encode(image), ProcessingOptions.defaults().withRotation(0));
            var rows = result.report().tables().getFirst().rows();
            assertTrue(rows.get(2).inkRatio() <= .015);
            assertFalse(rows.get(2).removed());
            assertTrue(rows.get(3).removed());
            assertTrue(rows.get(4).removed());
            assertTrue(rows.get(7).removed());
            var firstDiscarded = rows.stream().filter(ProcessingResult.Row::removed).findFirst().orElseThrow();
            assertEquals(440, firstDiscarded.top());
            assertEquals(BlankRowCutoff.CUTOFF, firstDiscarded.reason());
        } finally { image.release(); }
    }

    @Test void separatedDocumentsKeepIndependentConsecutiveBlankCutoffs() {
        Mat image = new Mat(1800, 1200, CvType.CV_8UC3, new Scalar(70, 110, 150));
        Scalar paper = new Scalar(230, 225, 215);
        try {
            Imgproc.rectangle(image, new Point(100, 50), new Point(1000, 1050), paper, -1);
            Imgproc.rectangle(image, new Point(230, 850), new Point(1130, 1720), paper, -1);
            Imgproc.line(image, new Point(230, 850), new Point(1130, 850), Scalar.all(140), 3);
            for (int[] origin : new int[][]{{180, 250}, {320, 1050}}) {
                for (int y = origin[1]; y <= origin[1] + 560; y += 80)
                    Imgproc.line(image, new Point(origin[0], y), new Point(origin[0] + 740, y), Scalar.all(0), 2);
                for (double x : new double[]{0, .132, .333, .450, .566, .683, .819, 1})
                    Imgproc.line(image, new Point(origin[0] + x * 740, origin[1]),
                            new Point(origin[0] + x * 740, origin[1] + 560), Scalar.all(0), 2);
            }
            Imgproc.putText(image, "UPPER", new Point(350, 450), Imgproc.FONT_HERSHEY_SIMPLEX, 1, Scalar.all(0), 2);
            Imgproc.putText(image, "LOWER START", new Point(450, 1310), Imgproc.FONT_HERSHEY_SIMPLEX, 1, Scalar.all(0), 2);
            Imgproc.putText(image, "LOWER", new Point(390, 1500), Imgproc.FONT_HERSHEY_SIMPLEX, 1, Scalar.all(0), 2);
            // This cutoff fixture has a tight three-row gap; exercise the configurable multiplier.
            var result = processor.process(encode(image), new ProcessingOptions(2200, 2, .00035, 0)
                    .withDocumentSeparationRowMultiplier(2.5));
            assertEquals(2, result.report().papers().size());
            var upper = result.report().papers().getFirst();
            var lower = result.report().papers().getLast();
            var upperTable = result.report().tables().stream().filter(t -> upper.tableIndices().contains(t.index())).findFirst().orElseThrow();
            var lowerTable = result.report().tables().stream().filter(t -> lower.tableIndices().contains(t.index())).findFirst().orElseThrow();
            assertTrue(upperTable.rows().stream().anyMatch(r -> r.reason().equals(BlankRowCutoff.CUTOFF)));
            assertTrue(lowerTable.rows().stream().noneMatch(r -> r.reason().equals(BlankRowCutoff.CUTOFF)));
            assertTrue(lowerTable.rows().stream().anyMatch(r -> !r.removed() && r.top() > 1400 && r.inkPixels() > 0));
            assertTrue(lowerTable.rows().stream().allMatch(r -> r.top() >= lower.bounds().y()));
            assertTrue(result.images().containsKey("paper-01-compacted.png"));
            assertTrue(result.images().containsKey("paper-02-compacted.png"));
        } finally { image.release(); }
    }

    private static byte[] encode(Mat image) {
        MatOfByte buffer = new MatOfByte();
        try { Imgcodecs.imencode(".png", image, buffer); return buffer.toArray(); }
        finally { buffer.release(); }
    }

    @Test void gridPerspectiveWorksWhenThePageBoundaryIsMissing() {
        Mat image = new Mat(1400, 1000, CvType.CV_8UC3, Scalar.all(255));
        Mat transformed = new Mat();
        MatOfPoint2f from = new MatOfPoint2f(new Point(0, 0), new Point(999, 0), new Point(999, 1399), new Point(0, 1399));
        MatOfPoint2f to = new MatOfPoint2f(new Point(30, 0), new Point(970, 20), new Point(999, 1399), new Point(0, 1370));
        Mat transform = Imgproc.getPerspectiveTransform(from, to);
        try {
            for (int y = 180; y <= 1220; y += 80) Imgproc.line(image, new Point(100, y), new Point(900, y), Scalar.all(0), 2);
            for (double x : new double[]{0, .132, .333, .450, .566, .683, .819, 1})
                Imgproc.line(image, new Point(100 + x * 800, 180), new Point(100 + x * 800, 1220), Scalar.all(0), 2);
            Imgproc.warpPerspective(image, transformed, transform, image.size(), Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar.all(255));
            var result = processor.process(encode(transformed), ProcessingOptions.defaults());
            assertEquals("GRID_PERSPECTIVE", result.report().correction());
            assertFalse(result.report().tables().isEmpty());
            assertTrue(result.report().tables().getFirst().removedRows() > 5);
        } finally { image.release(); transformed.release(); from.release(); to.release(); transform.release(); }
    }

    @Test void sidewaysTablesAreRotatedBeforeRowDetection() {
        Mat upright = new Mat(1400, 1000, CvType.CV_8UC3, Scalar.all(255));
        Mat sideways = new Mat();
        try {
            Imgproc.putText(upright, "DOCUMENT TITLE", new Point(100, 130), Imgproc.FONT_HERSHEY_SIMPLEX, 1.5, Scalar.all(0), 3);
            for (int y = 200; y <= 1200; y += 60) Imgproc.line(upright, new Point(100, y), new Point(900, y), Scalar.all(0), 2);
            for (int x = 100; x <= 900; x += 200) Imgproc.line(upright, new Point(x, 200), new Point(x, 1160), Scalar.all(0), 2);
            Core.rotate(upright, sideways, Core.ROTATE_90_CLOCKWISE);
            var result = processor.process(encode(sideways), ProcessingOptions.defaults());
            assertTrue(result.report().workingHeight() > result.report().workingWidth());
            assertFalse(result.report().tables().isEmpty());
            assertEquals(90, result.report().rotation().clockwiseDegrees() % 180);
        } finally { upright.release(); sideways.release(); }
    }
}
