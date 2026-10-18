package kr.co.slip.api;

/** Spatial thresholds use the default-size reference and scale with the actual input resize. */
public record ProcessingOptions(int maxDimension, int protectedTopRows,
                                double minimumInkRatio, int rotationDegrees, OutputMode outputMode) {
    public ProcessingOptions {
        java.util.Objects.requireNonNull(outputMode, "outputMode");
        if (maxDimension < 800 || maxDimension > 3000 || protectedTopRows < 1 || protectedTopRows > 10
                || !Double.isFinite(minimumInkRatio) || minimumInkRatio < 0 || minimumInkRatio > .1
                || (rotationDegrees != -1 && rotationDegrees != 0 && rotationDegrees != 90
                    && rotationDegrees != 180 && rotationDegrees != 270))
            throw new IllegalArgumentException("Invalid processing options");
    }
    public ProcessingOptions(int maxDimension, int protectedTopRows,
                             double minimumInkRatio, int rotationDegrees) {
        this(maxDimension, protectedTopRows, minimumInkRatio,
                rotationDegrees, OutputMode.ALL);
    }
    public ProcessingOptions(int maxDimension, int protectedTopRows,
                             double minimumInkRatio) {
        this(maxDimension, protectedTopRows, minimumInkRatio, -1);
    }
    /** -1 = automatic; otherwise an exact clockwise rotation, after EXIF decoding. */
    public ProcessingOptions withRotation(int degrees) {
        return new ProcessingOptions(maxDimension, protectedTopRows,
                minimumInkRatio, degrees, outputMode);
    }
    public ProcessingOptions withMaxDimension(int dimension) {
        return new ProcessingOptions(dimension, protectedTopRows,
                minimumInkRatio, rotationDegrees, outputMode);
    }
    public ProcessingOptions withMinimumInkRatio(double ratio) {
        return new ProcessingOptions(maxDimension, protectedTopRows, ratio, rotationDegrees, outputMode);
    }
    public ProcessingOptions withOutputMode(OutputMode mode) {
        return new ProcessingOptions(maxDimension, protectedTopRows,
                minimumInkRatio, rotationDegrees, mode);
    }
    public static ProcessingOptions defaults() { return new ProcessingOptions(2200, 2, .015); }
}
