package selectionwheel;

import java.awt.geom.Point2D;

/**
 * Pure, stateless math utilities used by {@link Wheel} and {@link WheelModel}.
 *
 * <p>None of the methods in this class touch Swing, the EDT, or any mutable
 * state. They are safe to call from any thread and are fully testable
 * without a graphics environment.
 *
 * <p>The {@code Point2D} parameter type comes from {@link java.awt.geom},
 * which is part of AWT (a pure math/layout package) and does not require
 * a windowing toolkit to be available.
 */
public final class WheelMath {

	private WheelMath() {
		// Utility class - no instances.
	}

	/**
	 * Returns the angular width (in degrees) of a single section of a wheel
	 * with the given number of sections.
	 *
	 * @param numSections must be {@code > 0}
	 * @return {@code 360.0 / numSections}
	 * @throws IllegalArgumentException if {@code numSections <= 0}
	 */
	public static double sectionAngleDeg(int numSections) {
		if (numSections <= 0) {
			throw new IllegalArgumentException(
					"numSections must be > 0 (was " + numSections + ")");
		}
		return 360.0 / numSections;
	}

	/**
	 * Normalizes an angle in degrees to the range {@code [-360, 360)} using
	 * Java's {@code %} operator (which preserves the sign of the dividend).
	 * This matches the original {@code Wheel.setRotationAngle} behavior.
	 *
	 * @param angleDeg any angle, positive or negative
	 * @return the equivalent angle in {@code [-360, 360)}
	 */
	public static double normalizeAngleDeg(double angleDeg) {
		return angleDeg % 360;
	}

	/**
	 * Returns the index of the section currently positioned under the tick
	 * (i.e., between {@code 0} and {@code sectionAngle} degrees).
	 *
	 * <p>This is the same computation as the original {@code Wheel.getSelectedItem}:
	 * <pre>
	 *   floor(numSections + (rotationAngleDeg % 360) / sectionAngle) % numSections
	 * </pre>
	 * The {@code + numSections} term guards against negative dividends
	 * (Java's {@code %} preserves the dividend's sign).
	 *
	 * @param rotationAngleDeg current rotation angle in degrees
	 * @param numSections      number of sections on the wheel
	 * @return a section index in {@code [0, numSections)}
	 * @throws IllegalArgumentException if {@code numSections <= 0}
	 */
	public static int selectionIndex(double rotationAngleDeg, int numSections) {
		double delta = sectionAngleDeg(numSections);
		return (int) Math.floor(numSections + (rotationAngleDeg % 360) / delta) % numSections;
	}

	/**
	 * Clamps a speed to the range {@code [0, maxSpeed]}.
	 *
	 * @param speed    any speed (typically already {@code >= 0})
	 * @param maxSpeed upper bound, must be {@code >= 0}
	 * @return {@code Math.max(0, Math.min(speed, maxSpeed))}
	 */
	public static double clampSpeed(double speed, double maxSpeed) {
		if (maxSpeed < 0) {
			throw new IllegalArgumentException("maxSpeed must be >= 0 (was " + maxSpeed + ")");
		}
		return Math.max(0, Math.min(speed, maxSpeed));
	}

	/**
	 * Computes the initial spin speed (in degrees per second, with sign
	 * indicating direction) from a mouse drag of {@code angleEndDeg -
	 * angleStartDeg} taking {@code timeEndMs - timeStartMs} milliseconds.
	 *
	 * <p>The magnitude is clamped to {@code maxSpeedDegPerSec}. Returns
	 * {@code 0} if the elapsed time is zero or the raw computation yields
	 * {@code NaN}/{@code Infinity} (no valid drag).
	 *
	 * @param angleStartDeg       rotation angle at mouse press
	 * @param angleEndDeg        rotation angle at mouse release
	 * @param timeStartMs        {@link System#currentTimeMillis()} at press
	 * @param timeEndMs          {@link System#currentTimeMillis()} at release
	 * @param maxSpeedDegPerSec  upper bound on the magnitude of the result
	 * @return signed speed in degrees per second, or {@code 0} for a no-op
	 * @throws IllegalArgumentException if {@code maxSpeedDegPerSec < 0}
	 */
	public static double computeInitialSpeedDegPerSec(
			double angleStartDeg, double angleEndDeg,
			long timeStartMs, long timeEndMs,
			double maxSpeedDegPerSec) {
		long elapsed = timeEndMs - timeStartMs;
		if (elapsed == 0) {
			return 0;
		}
		double raw = 1000.0 * (angleEndDeg - angleStartDeg) / elapsed;
		if (Double.isNaN(raw) || Double.isInfinite(raw)) {
			return 0;
		}
		int sign = (int) Math.signum(raw);
		return sign * clampSpeed(Math.abs(raw), maxSpeedDegPerSec);
	}

	/**
	 * Computes the angular delta (in degrees) between two mouse positions
	 * relative to a rotation center, using the angle-between-vectors formula
	 * from the original {@code Wheel.mouseDragged}.
	 *
	 * <p>Returns {@code 0} for the degenerate cases the original code
	 * silently dropped: vertical drag (where the slope calculation would
	 * divide by zero) and {@code NaN} results from the arctangent.
	 *
	 * @param prev   previous mouse position
	 * @param curr   current mouse position
	 * @param center wheel rotation center
	 * @return signed delta in degrees, or {@code 0} for degenerate inputs
	 */
	public static double dragDeltaDeg(Point2D prev, Point2D curr, Point2D center) {
		double dxPrev = prev.getX() - center.getX();
		double dxCurr = curr.getX() - center.getX();
		if (dxPrev == 0 || dxCurr == 0) {
			return 0;
		}
		double k1 = (prev.getY() - center.getY()) / dxPrev;
		double k2 = (curr.getY() - center.getY()) / dxCurr;
		double delta = Math.toDegrees(Math.atan((k2 - k1) / (1 + k2 * k1)));
		return Double.isNaN(delta) ? 0 : delta;
	}
}
