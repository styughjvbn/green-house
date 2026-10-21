package kr.co.slip.api;

/** Spatial thresholds use the default-size reference and scale with the actual input resize. */
public record ProcessingOptions(int maxDimension, int protectedTopRows,
                                double minimumInkRatio, int rotationDegrees, OutputMode outputMode,
                                double documentSeparationRowMultiplier, double documentHeaderRowMultiplier) {
    public ProcessingOptions {
        java.util.Objects.requireNonNull(outputMode, "outputMode");
        if (maxDimension < 800 || maxDimension > 3000 || protectedTopRows < 1 || protectedTopRows > 10
                || !Double.isFinite(minimumInkRatio) || minimumInkRatio < 0 || minimumInkRatio > .1
                || !Double.isFinite(documentSeparationRowMultiplier) || documentSeparationRowMultiplier <= 0
                || !Double.isFinite(documentHeaderRowMultiplier) || documentHeaderRowMultiplier <= 0
                || (rotationDegrees != -1 && rotationDegrees != 0 && rotationDegrees != 90
                    && rotationDegrees != 180 && rotationDegrees != 270))
            throw new IllegalArgumentException("Invalid processing options");
    }
    public ProcessingOptions(int maxDimension, int protectedTopRows, double minimumInkRatio,
                             int rotationDegrees, OutputMode outputMode, double documentSeparationRowMultiplier) {
        this(maxDimension, protectedTopRows, minimumInkRatio, rotationDegrees, outputMode,
                documentSeparationRowMultiplier, 7);
    }
    public ProcessingOptions(int maxDimension, int protectedTopRows,
                             double minimumInkRatio, int rotationDegrees, OutputMode outputMode) {
        this(maxDimension, protectedTopRows, minimumInkRatio, rotationDegrees, outputMode, 5.5);
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
                minimumInkRatio, degrees, outputMode, documentSeparationRowMultiplier, documentHeaderRowMultiplier);
    }
    public ProcessingOptions withMaxDimension(int dimension) {
        return new ProcessingOptions(dimension, protectedTopRows,
                minimumInkRatio, rotationDegrees, outputMode, documentSeparationRowMultiplier, documentHeaderRowMultiplier);
    }
    public ProcessingOptions withMinimumInkRatio(double ratio) {
        return new ProcessingOptions(maxDimension, protectedTopRows, ratio, rotationDegrees, outputMode, documentSeparationRowMultiplier, documentHeaderRowMultiplier);
    }
    public ProcessingOptions withOutputMode(OutputMode mode) {
        return new ProcessingOptions(maxDimension, protectedTopRows,
                minimumInkRatio, rotationDegrees, mode, documentSeparationRowMultiplier, documentHeaderRowMultiplier);
    }
    public ProcessingOptions withDocumentSeparationRowMultiplier(double multiplier) {
        return new ProcessingOptions(maxDimension, protectedTopRows,
                minimumInkRatio, rotationDegrees, outputMode, multiplier, documentHeaderRowMultiplier);
    }
    public ProcessingOptions withDocumentHeaderRowMultiplier(double multiplier) {
        return new ProcessingOptions(maxDimension, protectedTopRows,
                minimumInkRatio, rotationDegrees, outputMode, documentSeparationRowMultiplier, multiplier);
    }
    public static ProcessingOptions defaults() { return new ProcessingOptions(2048, 2, .015); }
}
