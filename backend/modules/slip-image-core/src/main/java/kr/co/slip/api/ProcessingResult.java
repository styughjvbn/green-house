package kr.co.slip.api;

import java.util.List;
import java.util.Map;

public record ProcessingResult(Report report, Map<String, byte[]> images) {
    public record XY(double x, double y) {}
    public record Box(int x, int y, int width, int height) {}
    public record Row(int index, int top, int bottom, double inkRatio, int inkPixels,
                      boolean removed, String reason) {}
    public record Table(int index, Box bounds, List<Integer> horizontalLines,
                        List<Integer> verticalLines, List<Row> rows, int removedRows, DocumentForm form) {
        public Table(int index, Box bounds, List<Integer> horizontalLines,
                     List<Integer> verticalLines, List<Row> rows, int removedRows) {
            this(index, bounds, horizontalLines, verticalLines, rows, removedRows, DocumentForm.UNKNOWN);
        }
    }
    public record Rotation(String mode, int clockwiseDegrees, double deskewCounterClockwiseDegrees,
                           boolean reviewRequired, String method) {}
    public record Paper(int index, Box bounds, List<XY> contour, String method,
                        boolean reviewRequired, List<Integer> tableIndices) {}
    public record Report(String correction, int sourceWidth, int sourceHeight,
                         int workingWidth, int workingHeight, List<XY> documentCorners,
                         List<String> warnings, List<Table> tables, Rotation rotation, List<Paper> papers,
                         List<Double> workingToSource) {
        /** Axis-aligned envelope in the EXIF-oriented source image, clipped to its bounds. */
        public Box sourceBounds(Box area) {
            double minX = sourceWidth, minY = sourceHeight, maxX = 0, maxY = 0;
            for (double y : new double[]{area.y(), area.y() + area.height()})
                for (double x : new double[]{area.x(), area.x() + area.width()}) {
                    double w = workingToSource.get(6)*x + workingToSource.get(7)*y + workingToSource.get(8);
                    double sx = (workingToSource.get(0)*x + workingToSource.get(1)*y + workingToSource.get(2))/w;
                    double sy = (workingToSource.get(3)*x + workingToSource.get(4)*y + workingToSource.get(5))/w;
                    if (!Double.isFinite(sx) || !Double.isFinite(sy)) throw new IllegalStateException("Invalid source coordinates");
                    minX = Math.min(minX, sx); minY = Math.min(minY, sy);
                    maxX = Math.max(maxX, sx); maxY = Math.max(maxY, sy);
                }
            int left = Math.max(0, Math.min(sourceWidth, (int) Math.floor(minX)));
            int top = Math.max(0, Math.min(sourceHeight, (int) Math.floor(minY)));
            int right = Math.max(left, Math.min(sourceWidth, (int) Math.ceil(maxX)));
            int bottom = Math.max(top, Math.min(sourceHeight, (int) Math.ceil(maxY)));
            return new Box(left, top, right-left, bottom-top);
        }
    }
}
