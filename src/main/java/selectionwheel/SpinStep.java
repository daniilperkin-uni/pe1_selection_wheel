package selectionwheel;

/**
 * Immutable value representing one tick of the wheel's decelerating spin.
 *
 * <p>Created by {@link #compute(double, int, double, double)}, which is
 * a pure function: given the same inputs it returns the same
 * {@code SpinStep}. This makes the wheel's spin physics fully testable
 * without instantiating Swing or a real timer.
 *
 * @param deltaDeg   signed rotation delta to apply this tick (degrees)
 * @param newSpeed   spin speed after this tick (degrees per second,
 *                   {@code >= 0})
 * @param shouldStop {@code true} when the spin has decelerated to rest
 *                   and the timer should stop
 */
public record SpinStep(double deltaDeg, double newSpeed, boolean shouldStop) {

	/**
	 * Computes the next spin step from the current spin state.
	 *
	 * <p>Given a spin moving at {@code currentSpeed} deg/s in
	 * {@code direction} ({@code +1} = counter-clockwise,
	 * {@code -1} = clockwise), decelerating at {@code deceleration}
	 * deg/s^2, over a tick of {@code dtSeconds}:
	 * <ul>
	 *   <li>{@code deltaDeg = direction * currentSpeed * dt}</li>
	 *   <li>{@code newSpeed = currentSpeed + deceleration * dt}</li>
	 *   <li>{@code shouldStop = (newSpeed <= 0)} (decelerated to rest)</li>
	 * </ul>
	 *
	 * @param currentSpeed  current angular speed (deg/s, {@code >= 0});
	 *                      if {@code <= 0}, the wheel is already stopped
	 * @param direction     {@code +1} or {@code -1} for direction;
	 *                      {@code 0} is treated as already stopped
	 * @param deceleration  {@code <= 0}; deg/s^2
	 * @param dtSeconds     tick duration in seconds ({@code > 0})
	 * @return a {@link SpinStep} describing this tick's motion
	 */
	public static SpinStep compute(double currentSpeed, int direction,
			double deceleration, double dtSeconds) {
		if (dtSeconds <= 0) {
			throw new IllegalArgumentException(
					"dtSeconds must be > 0 (was " + dtSeconds + ")");
		}
		if (currentSpeed <= 0 || direction == 0) {
			return new SpinStep(0, 0, true);
		}
		double delta = direction * (currentSpeed * dtSeconds);
		double nextSpeed = currentSpeed + deceleration * dtSeconds;
		if (nextSpeed <= 0) {
			return new SpinStep(delta, 0, true);
		}
		return new SpinStep(delta, nextSpeed, false);
	}
}
