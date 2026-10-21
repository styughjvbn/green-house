package kr.co.slip.opencv;

import java.util.ArrayList;
import java.util.List;
import kr.co.slip.api.ProcessingResult.Table;

/** Uses grid geometry only; never depends on later ink or row-removal decisions. */
final class TableSpacing {
    private TableSpacing() {}

    static boolean separate(Table above, Table below, double multiplier) {
        double average = averageRowHeight(above, below);
        int gap = below.bounds().y() - above.bounds().y() - above.bounds().height();
        return average > 0 && gap >= average * multiplier;
    }

    static double averageRowHeight(Table above, Table below) {
        List<Integer> upper = bodyHeights(above), lower = bodyHeights(below);
        if (upper.isEmpty() || lower.isEmpty()) return 0;
        return (upper.stream().mapToInt(Integer::intValue).sum()
                + lower.stream().mapToInt(Integer::intValue).sum()) / (double) (upper.size() + lower.size());
    }

    private static List<Integer> bodyHeights(Table table) {
        List<Integer> lines = table.horizontalLines().stream().distinct().sorted().toList();
        List<Integer> heights = new ArrayList<>();
        // Exclude the first two intervals when enough geometry remains for a body estimate.
        int start = lines.size() >= 5 ? 2 : 0;
        for (int i = start; i + 1 < lines.size(); i++) {
            int height = lines.get(i + 1) - lines.get(i);
            if (height > 0) heights.add(height);
        }
        if (heights.isEmpty()) return List.of();
        List<Integer> sorted = heights.stream().sorted().toList();
        double median = sorted.size() % 2 == 0
                ? (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2.
                : sorted.get(sorted.size() / 2);
        // Ignore duplicate line peaks and oversized merged intervals.
        return heights.stream().filter(h -> h >= median * .5 && h <= median * 1.5).toList();
    }
}
