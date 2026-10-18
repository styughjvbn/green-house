package kr.co.slip.opencv;

import java.util.List;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

/** The two supported printed forms, identified by their column and merged-header geometry. */
record TableLayout(Kind kind, int headerRows, int printedLeft, int printedRight) {
    enum Kind { CONSIGNMENT_RECEIPT, LISTING_REQUEST, UNKNOWN }
    private static final double[] RECEIPT_COLUMNS = {0, .116, .376, .420, .464, .509, .552, .693, .786, .879, 1};
    private static final double[] LISTING_COLUMNS = {0, .132, .333, .450, .566, .683, .819, 1};
    static final TableLayout UNKNOWN = new TableLayout(Kind.UNKNOWN, 0, 0, 0);

    static TableLayout detect(Mat grid, Rect region, List<Integer> xs, List<Integer> ys, PixelScale pixels) {
        if (ys.size() < 3 || xs.size() < 2) return UNKNOWN;
        // Normalize against visible outer grid lines rather than padding in the table ROI.
        int origin = xs.getFirst() < region.width * .05 ? xs.getFirst() : 0;
        int width = xs.getFirst() < region.width * .05 && xs.getLast() > region.width * .90
                ? xs.getLast() - origin : region.width;
        if (!matches(xs, origin, width, RECEIPT_COLUMNS) && !matches(xs, origin, width, LISTING_COLUMNS)) {
            // A curved/missing outer stroke can shift that edge; try the detected ROI itself.
            origin = 0;
            width = region.width;
        }
        if (matches(xs, origin, width, RECEIPT_COLUMNS)) {
            int bodyStart = -1;
            for (int row = 0; row < Math.min(6, ys.size() - 1); row++) {
                int inset = Math.min(pixels.length(5), Math.max(1, (ys.get(row + 1) - ys.get(row)) / 8));
                int top = region.y + ys.get(row) + inset;
                int height = ys.get(row + 1) - ys.get(row) - 2 * inset;
                if (height <= 0) continue;
                boolean divided = true;
                for (int column : new int[]{3, 4, 5}) {
                    int x = nearest(xs, origin + width * RECEIPT_COLUMNS[column]);
                    try (MatScope scope = new MatScope()) {
                        Mat stripe = scope.own(grid.submat(new Rect(region.x + x, top, 1, height)));
                        if (Core.countNonZero(stripe) < height * .60) divided = false;
                    }
                }
                if (divided) { bodyStart = row; break; }
            }
            // Require a merged header before the four printed grade-choice cells.
            if (bodyStart > 0)
                return new TableLayout(Kind.CONSIGNMENT_RECEIPT, bodyStart,
                        nearest(xs, origin + width * .376), nearest(xs, origin + width * .552));
        }
        if (matches(xs, origin, width, LISTING_COLUMNS))
            return new TableLayout(Kind.LISTING_REQUEST, 1, 0, 0);
        return UNKNOWN;
    }

    private static boolean matches(List<Integer> xs, int origin, int width, double[] expected) {
        if (xs.size() < expected.length - 1 || xs.size() > expected.length + 2) return false;
        // A clipped outer border is allowed; every internal column boundary must be visible.
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
