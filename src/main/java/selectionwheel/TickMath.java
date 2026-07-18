package selectionwheel;

import java.awt.Polygon;

/**
 * Pure utility for tick (pointer) geometry.
 *
 * <p>Extracted from {@link Tick} so the polygon math can be tested
 * without instantiating a Swing component. Unlike the original
 * {@code Tick.adjustPolygon}, these methods never mutate their inputs.
 */
public final class TickMath {

	private TickMath() {
		// Utility class - no instances.
	}

	/**
	 * Returns a new polygon that is the input scaled to fit
	 * {@code panelWidth} and translated so its center aligns with the
	 * panel center. The input {@code source} is left untouched
	 * (defensive copy).
	 *
	 * <p>This replaces the original {@code Tick.adjustPolygon} which
	 * mutated {@code source.xpoints[i]} in place. Calling the original
	 * twice with the same reference compounded the scaling factor; the
	 * new implementation is idempotent.
	 *
	 * @param source      the polygon to scale and center
	 * @param panelWidth  target panel width ({@code > 0})
	 * @param panelHeight target panel height
	 * @return a new {@link Polygon}, never the input
	 * @throws IllegalArgumentException if {@code source} is null or
	 *                                  has no points, or if
	 *                                  {@code panelWidth <= 0}
	 */
	public static Polygon adjustPolygon(Polygon source, int panelWidth, int panelHeight) {
		if (source == null) {
			throw new IllegalArgumentException("source must not be null");
		}
		if (source.npoints == 0) {
			throw new IllegalArgumentException("source must have at least one point");
		}
		if (panelWidth <= 0) {
			throw new IllegalArgumentException(
					"panelWidth must be > 0 (was " + panelWidth + ")");
		}
		// Defensive copy - never mutate the caller's arrays.
		int[] xs = source.xpoints.clone();
		int[] ys = source.ypoints.clone();
		int n = source.npoints;

		// Compute natural width of the polygon.
		int xmax = Integer.MIN_VALUE, xmin = Integer.MAX_VALUE;
		for (int i = 0; i < n; i++) {
			if (xs[i] > xmax) xmax = xs[i];
			if (xs[i] < xmin) xmin = xs[i];
		}
		int width = xmax - xmin;
		if (width == 0) {
			width = 1; // avoid divide-by-zero for degenerate polygons
		}
		double factor = (double) panelWidth / width;
		for (int i = 0; i < n; i++) {
			xs[i] = (int) (xs[i] * factor);
			ys[i] = (int) (ys[i] * factor);
		}

		// Translate so the polygon's centroid sits at the panel center.
		int centerX = 0, centerY = 0;
		for (int i = 0; i < n; i++) {
			centerX += xs[i];
			centerY += ys[i];
		}
		centerX /= n;
		centerY /= n;
		int dx = panelWidth / 2 - centerX;
		int dy = panelHeight / 2 - centerY;
		for (int i = 0; i < n; i++) {
			xs[i] += dx;
			ys[i] += dy;
		}
		return new Polygon(xs, ys, n);
	}

	/**
	 * Returns the default tick shape: an isoceles triangle pointing
	 * left, anchored at {@code (0, height/2)} and opening to the right
	 * edge of the panel.
	 *
	 * @param width  panel width ({@code > 0})
	 * @param height panel height ({@code > 0})
	 * @return a new 3-point {@link Polygon}
	 */
	public static Polygon computeDefaultTriangle(int width, int height) {
		Polygon polygon = new Polygon();
		polygon.addPoint(0, height / 2);
		polygon.addPoint(width, (int) (height / 2 - width * Math.tan(Math.toRadians(30))));
		polygon.addPoint(width, (int) (height / 2 + width * Math.tan(Math.toRadians(30))));
		return polygon;
	}
}
