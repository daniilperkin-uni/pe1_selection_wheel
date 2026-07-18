package selectionwheel;

/**
 * Observer interface for {@link Wheel} lifecycle events.
 *
 * <p>All methods are invoked on the Swing Event Dispatch Thread.
 * Implementations must not perform blocking work.
 *
 * <p>Events fired:
 * <ul>
 *   <li>{@link #selectionChanged(String)} - whenever the wheel's selected
 *       item changes (rotation crossed a section boundary).</li>
 *   <li>{@link #spinStarted()} - when a spin begins (mouse release or
 *       programmatic {@link Wheel#spinStartAsync}).</li>
 *   <li>{@link #spinStopped()} - when the wheel comes to rest.</li>
 *   <li>{@link #spinSpeedChanged(double)} - while spinning, periodically
 *       reports the current speed in degrees per second.</li>
 *   <li>{@link #rotationChanged(double)} - whenever the rotation angle
 *       is updated, either by drag or by spin.</li>
 * </ul>
 */
public interface WheelListener {

    /** Called when the section currently under the tick changes. */
    default void selectionChanged(String selectedItem) {
    }

    /** Called once when a spin starts. */
    default void spinStarted() {
    }

    /** Called once when the wheel comes to rest. */
    default void spinStopped() {
    }

    /**
     * Called periodically while the wheel is spinning.
     *
     * @param speedDegreesPerSecond current spin speed (always {@code >= 0})
     */
    default void spinSpeedChanged(double speedDegreesPerSecond) {
    }

    /**
     * Called whenever the wheel's rotation angle is updated.
     *
     * @param angleDegrees current rotation angle in degrees,
     *                     normalized to {@code [0, 360)}
     */
    default void rotationChanged(double angleDegrees) {
    }
}
