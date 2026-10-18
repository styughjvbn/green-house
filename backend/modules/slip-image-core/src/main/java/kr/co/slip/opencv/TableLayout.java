package kr.co.slip.opencv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

/** Header merging identifies the structure; column proportions corroborate the supported form. */
record TableLayout(Kind kind, int headerRows, int printedLeft, int printedRight) {
    enum Kind { CONSIGNMENT_RECEIPT, LISTING_REQUEST, UNKNOWN }
    private static final double[] RECEIPT_COLUMNS = {0, .116, .376, .420, .464, .509, .552, .693, .786, .879, 1};
    private static final double[] LISTING_COLUMNS = {0, .132, .333, .450, .566, .683, .819, 1};
    static final TableLayout UNKNOWN = new TableLayout(Kind.UNKNOWN, 0, 0, 0);

    static TableLayout detect(Mat grid, Rect region, List<Integer> xs, List<Integer> ys, PixelScale pixels) {
        if (ys.size() < 3 || xs.size() < 2) return UNKNOWN;
        int origin = xs.getFirst() < region.width * .05 ? xs.getFirst() : 0;
        int width = xs.getFirst() < region.width * .05 && xs.getLast() > region.width * .90
                ? xs.getLast() - origin : region.width;
        List<Integer> internal = xs.stream().filter(x -> x > region.width * .04 && x < region.width * .96).toList();
        if (internal.size() < 2) return UNKNOWN;
        List<HeaderRow> rows = headerRows(grid, region, xs, ys, internal, pixels);
        int bodyStart = mergedHeaderEnd(rows);
        if (bodyStart > 0 && proportionsMatch(xs, origin, width, region.width, RECEIPT_COLUMNS))
            return new TableLayout(Kind.CONSIGNMENT_RECEIPT, bodyStart,
                    nearest(xs, origin + width * .376), nearest(xs, origin + width * .552));
        // Missing or unreadable lines do not establish a non-merged header.
        if (bodyStart < 0 && rows.size() >= 2 && rows.get(0).allDivided() && rows.get(1).allDivided()
                && proportionsMatch(xs, origin, width, region.width, LISTING_COLUMNS))
            return new TableLayout(Kind.LISTING_REQUEST, 1, 0, 0);
        return UNKNOWN;
    }

    private record HeaderRow(int index, double[] coverage) {
        boolean allDivided() { return Arrays.stream(coverage).allMatch(value -> value >= .60); }
    }

    private static List<HeaderRow> headerRows(Mat grid, Rect region, List<Integer> xs, List<Integer> ys,
                                               List<Integer> internal, PixelScale pixels) {
        List<Integer> heights = new ArrayList<>();
        for (int row = 0; row < ys.size() - 1; row++) heights.add(ys.get(row + 1) - ys.get(row));
        heights.sort(Integer::compare);
        int typicalHeight = heights.get(heights.size() / 2);
        List<HeaderRow> result = new ArrayList<>();
        for (int row = 0; row < Math.min(7, ys.size() - 1); row++) {
            int rowHeight = ys.get(row + 1) - ys.get(row);
            // A slanted horizontal boundary can produce two nearby projection peaks.
            if (rowHeight < typicalHeight * .40) continue;
            int inset = Math.min(pixels.length(5), Math.max(1, rowHeight / 8));
            int top = region.y + ys.get(row) + inset, height = rowHeight - 2 * inset;
            if (height <= 0) continue;
            double[] coverage = new double[internal.size()];
            for (int column = 0; column < internal.size(); column++) {
                int x = internal.get(column), gap = region.width;
                for (int other : xs) if (other != x) gap = Math.min(gap, Math.abs(x - other));
                int radius = Math.max(1, Math.min((int) Math.ceil(region.width * .015), gap / 4));
                int left = Math.max(0, x - radius), right = Math.min(region.width, x + radius + 1);
                try (MatScope scope = new MatScope()) {
                    Mat stripe = scope.own(grid.submat(new Rect(region.x + left, top, right - left, height)));
                    Mat occupiedRows = scope.mat();
                    Core.reduce(stripe, occupiedRows, 1, Core.REDUCE_MAX, CvType.CV_8U);
                    coverage[column] = Core.countNonZero(occupiedRows) / (double) height;
                }
            }
            result.add(new HeaderRow(row, coverage));
        }
        return result;
    }

    private static int mergedHeaderEnd(List<HeaderRow> rows) {
        for (int body = 1; body < rows.size() - 1; body++) {
            HeaderRow current = rows.get(body), next = rows.get(body + 1);
            if (next.index() != current.index() + 1) continue;
            for (int header = 0; header < body; header++) {
                double[] before = rows.get(header).coverage();
                // Other continuous dividers distinguish a merged cell from a missing grid.
                if (Arrays.stream(before).filter(value -> value >= .60).count() < 2) continue;
                int divisions = 0;
                for (int column = 0; column < before.length; column++)
                    if (before[column] <= .20 && current.coverage()[column] >= .60 && next.coverage()[column] >= .60)
                        divisions++;
                if (divisions >= 2) return current.index();
            }
        }
        return -1;
    }

    private static boolean proportionsMatch(List<Integer> xs, int origin, int width, int cropWidth, double[] expected) {
        return matches(xs, origin, width, expected) || matches(xs, 0, cropWidth, expected);
    }

    private static boolean matches(List<Integer> xs, int origin, int width, double[] expected) {
        if (xs.size() < expected.length - 1 || xs.size() > expected.length + 2) return false;
        for (int i = 1; i < expected.length - 1; i++)
            if (Math.abs((nearest(xs, origin + width * expected[i]) - origin) / (double) width - expected[i]) > .025) return false;
        for (int x : xs) {
            double distance = 1;
            for (double position : expected) distance = Math.min(distance, Math.abs((x - origin) / (double) width - position));
            if (distance > .025) return false;
        }
        return true;
    }

    private static int nearest(List<Integer> xs, double target) {
        return xs.stream().min(java.util.Comparator.comparingDouble(x -> Math.abs(x - target))).orElseThrow();
    }
}
