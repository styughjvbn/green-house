package kr.co.slip.opencv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import kr.co.slip.api.ProcessingResult.Row;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

/** Shares the same removed-row intervals between table crops and paper crops. */
final class RowCompactor {
    private RowCompactor() {}

    /** originY and endY use corrected-image coordinates; input may be a paper/table ROI. */
    static Mat compact(Mat input, int originY, int endY, List<Row> rows, MatScope scope) {
        int end = Math.min(input.rows(), endY - originY);
        if (end <= 0) throw new IllegalArgumentException("Empty compaction area");
        List<Mat> kept = new ArrayList<>();
        int previous = 0;
        for (Row row : rows.stream().filter(Row::removed).sorted(Comparator.comparingInt(Row::top)).toList()) {
            int top = Math.max(0, Math.min(end, row.top() - originY));
            int bottom = Math.max(top, Math.min(end, row.bottom() - originY));
            if (bottom <= previous) continue;
            if (top > previous) kept.add(scope.own(input.submat(new Rect(0, previous, input.cols(), top - previous))));
            previous = bottom;
        }
        if (previous < end) kept.add(scope.own(input.submat(new Rect(0, previous, input.cols(), end - previous))));
        if (kept.isEmpty()) throw new IllegalArgumentException("No retained image rows");
        Mat output = scope.mat();
        Core.vconcat(kept, output);
        return output;
    }
}
