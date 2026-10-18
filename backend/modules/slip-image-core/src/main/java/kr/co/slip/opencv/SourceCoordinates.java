package kr.co.slip.opencv;

import java.util.Arrays;
import java.util.List;
import org.opencv.core.Mat;

/** Composes only transforms applied to the actual image, never orientation probes. */
final class SourceCoordinates {
    private double[] forward;

    SourceCoordinates(int sourceWidth, int sourceHeight, int width, int height) {
        forward = new double[]{(double) width / sourceWidth, 0, 0, 0, (double) height / sourceHeight, 0, 0, 0, 1};
    }

    void quarterTurn(int turns, int width, int height) {
        switch (turns) {
            case 0 -> { }
            case 1 -> append(new double[]{0, -1, height - 1, 1, 0, 0, 0, 0, 1});
            case 2 -> append(new double[]{-1, 0, width - 1, 0, -1, height - 1, 0, 0, 1});
            case 3 -> append(new double[]{0, 1, 0, -1, 0, width - 1, 0, 0, 1});
            default -> throw new IllegalArgumentException("Invalid rotation");
        }
    }

    void append(Mat transform) {
        double[] next = new double[9];
        next[8] = 1;
        for (int y = 0; y < transform.rows(); y++)
            for (int x = 0; x < 3; x++) next[y * 3 + x] = transform.get(y, x)[0];
        append(next);
    }

    void append(double[] next) {
        double[] product = new double[9];
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++)
            for (int k = 0; k < 3; k++) product[y * 3 + x] += next[y * 3 + k] * forward[k * 3 + x];
        forward = product;
    }

    List<Double> inverse() {
        double a=forward[0], b=forward[1], c=forward[2], d=forward[3], e=forward[4], f=forward[5], g=forward[6], h=forward[7], i=forward[8];
        double determinant = a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
        if (!Double.isFinite(determinant) || Math.abs(determinant) < 1e-12)
            throw new IllegalStateException("Image coordinate transform is singular");
        return Arrays.stream(new double[]{e*i-f*h, c*h-b*i, b*f-c*e, f*g-d*i, a*i-c*g, c*d-a*f, d*h-e*g, b*g-a*h, a*e-b*d})
                .map(value -> value / determinant).boxed().toList();
    }
}
