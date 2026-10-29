package selectionwheel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure, testable state holder for a wheel of fortune.
 *
 * <p>Holds the list of selectable items, the current rotation angle,
 * and the spin animation state (speed, direction, deceleration).
 * Provides methods to mutate state and to advance the spin one tick
 * at a time via {@link #tickSpin(double)}.
 *
 * <p>This class is intentionally Swing-free: it does not extend any
 * JComponent, does not call repaint, and does not touch the EDT.
 * All state mutations are synchronous and deterministic, which makes
 * the model fully unit-testable without a graphics environment.
 *
 * <p>The {@link Wheel} view holds a {@code WheelModel} instance and
 * delegates state queries to it, while performing rendering and
 * event delivery itself.
 */
public final class WheelModel {

	/**
	 * Maximum number of items the wheel can display. This is the single
	 * source of truth for the limit; {@link ContentReader#ITEM_LIMIT}
	 * derives its value from this constant.
	 */
	public static final int ITEM_LIMIT = 100;

	private List<String> items;
	private int numSections;
	private double rotationAngleDeg;

	private double maxSpinSpeedDegPerSec = 360;
	private double spinDeceleration = -20;

	private boolean spinning;
	private double activeSpinSpeed;
	private int activeSpinDirection;
	private double activeSpinDeceleration;

	/**
	 * @param items the initial list of selectable items
	 * @throws IllegalArgumentException if {@code items} is null, empty,
	 *                                  or larger than {@link #ITEM_LIMIT}
	 */
	public WheelModel(List<String> items) {
		setItems(items);
	}

	/**
	 * Replaces the list of selectable items. Resets the rotation angle
	 * to {@code 0} and stops any in-flight spin.
	 *
	 * @param items new list of items; no element may be {@code null}
	 * @throws IllegalArgumentException if {@code items} is null, empty,
	 *                                  or larger than {@link #ITEM_LIMIT}
	 * @throws NullPointerException     if any element is {@code null}
	 */
	public void setItems(List<String> items) {
		if (items == null) {
			throw new IllegalArgumentException("items must not be null");
		}
		int n = items.size();
		if (n == 0) {
			throw new IllegalArgumentException("items must not be empty (would produce NaN delta)");
		}
		if (n > ITEM_LIMIT) {
			throw new IllegalArgumentException(
					"items size " + n + " exceeds limit " + ITEM_LIMIT);
		}
		// A null label would NPE in WheelRenderer on the EDT during the
		// first paint, so reject it at the model boundary instead.
		for (int i = 0; i < n; i++) {
			Objects.requireNonNull(items.get(i), "items[" + i + "] must not be null");
		}
		this.items = new ArrayList<>(items);
		this.numSections = n;
		this.rotationAngleDeg = 0;
		this.spinning = false;
		this.activeSpinSpeed = 0;
	}

	/** @return a defensive copy of the items list */
	public List<String> getItems() {
		return new ArrayList<>(items);
	}

	/** @return the number of sections (same as {@code getItems().size()}) */
	public int getNumSections() {
		return numSections;
	}

	/** @return the angular width (degrees) of a single section */
	public double getSectionAngleDeg() {
		return WheelMath.sectionAngleDeg(numSections);
	}

	/** @return the current rotation angle in degrees, in {@code [-360, 360)} */
	public double getRotationAngleDeg() {
		return rotationAngleDeg;
	}

	/**
	 * Sets the rotation angle, normalizing it to {@code [-360, 360)}.
	 *
	 * @param angleDeg any angle, positive or negative
	 */
	public void setRotationAngleDeg(double angleDeg) {
		this.rotationAngleDeg = WheelMath.normalizeAngleDeg(angleDeg);
	}

	/** @return the index of the section currently under the tick */
	public int getSelectedSectionIndex() {
		return WheelMath.selectionIndex(rotationAngleDeg, numSections);
	}

	/** @return the label of the section currently under the tick */
	public String getSelectedItem() {
		return items.get(getSelectedSectionIndex());
	}

	public boolean isSpinning() {
		return spinning;
	}

	/**
	 * @return current spin speed in deg/s, or {@code 0} if not spinning
	 */
	public double getSpinSpeedDegPerSec() {
		return spinning ? activeSpinSpeed : 0;
	}

	public double getMaxSpinSpeedDegPerSec() {
		return maxSpinSpeedDegPerSec;
	}

	/**
	 * @param v maximum spin speed in deg/s; must be {@code > 0} and finite
	 * @throws IllegalArgumentException if {@code v <= 0} or not finite
	 */
	public void setMaxSpinSpeedDegPerSec(double v) {
		// A non-positive or NaN limit used to slip through and then blew up
		// in WheelMath.clampSpeed on every drag release, inside the EDT
		// handler, which silently killed the spin feature for good.
		if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0) {
			throw new IllegalArgumentException(
					"maxSpinSpeed must be > 0 and finite (was " + v + ")");
		}
		this.maxSpinSpeedDegPerSec = v;
	}

	public double getSpinDeceleration() {
		return spinDeceleration;
	}

	/**
	 * @param deceleration deg/s^2; must be {@code <= 0} and finite
	 * @throws IllegalArgumentException if {@code deceleration > 0} or not finite
	 */
	public void setSpinDeceleration(double deceleration) {
		if (Double.isNaN(deceleration) || Double.isInfinite(deceleration) || deceleration > 0) {
			throw new IllegalArgumentException(
					"deceleration must be <= 0 and finite (was " + deceleration + ")");
		}
		this.spinDeceleration = deceleration;
	}

	/**
	 * Starts a decelerating spin. No-op if {@code speedDegPerSec <= 0}
	 * or {@code direction == 0}.
	 *
	 * @param speedDegPerSec initial speed magnitude (deg/s), finite
	 * @param direction      {@code +1} for clockwise on screen, {@code -1} for
	 *                       counter-clockwise
	 * @param deceleration   deg/s^2, must be {@code <= 0} and finite
	 * @throws IllegalArgumentException if {@code deceleration > 0}, or if
	 *                                  speed or deceleration is not finite
	 */
	public void startSpin(double speedDegPerSec, int direction, double deceleration) {
		if (Double.isNaN(deceleration) || Double.isInfinite(deceleration) || deceleration > 0) {
			throw new IllegalArgumentException(
					"deceleration must be <= 0 and finite (was " + deceleration + ")");
		}
		// A NaN/infinite speed passes the "<= 0" no-op test below and then
		// poisons the rotation angle: spinning stays true forever and the
		// renderer is asked to paint NaN coordinates.
		if (Double.isNaN(speedDegPerSec) || Double.isInfinite(speedDegPerSec)) {
			throw new IllegalArgumentException(
					"speedDegPerSec must be finite (was " + speedDegPerSec + ")");
		}
		if (speedDegPerSec <= 0 || direction == 0) {
			return;
		}
		this.spinning = true;
		this.activeSpinSpeed = speedDegPerSec;
		this.activeSpinDirection = direction;
		this.activeSpinDeceleration = deceleration;
	}

	/**
	 * Advances the spin by one tick of {@code dtSeconds}.
	 *
	 * @param dtSeconds tick duration in seconds ({@code > 0}, finite)
	 * @return the {@link SpinStep} that describes this tick
	 * @throws IllegalArgumentException if {@code dtSeconds} is not a
	 *                                  positive finite number
	 */
	public SpinStep tickSpin(double dtSeconds) {
		// NaN compares false against every bound: the plain "<= 0" guard let
		// it through, SpinStep.compute then produced NaN delta/newSpeed and
		// the model stayed spinning forever with a NaN rotation angle.
		if (!Double.isFinite(dtSeconds) || dtSeconds <= 0) {
			throw new IllegalArgumentException(
					"dtSeconds must be > 0 and finite (was " + dtSeconds + ")");
		}
		if (!spinning) {
			return new SpinStep(0, 0, true);
		}
		SpinStep step = SpinStep.compute(
				activeSpinSpeed, activeSpinDirection, activeSpinDeceleration, dtSeconds);
		rotationAngleDeg = WheelMath.normalizeAngleDeg(rotationAngleDeg + step.deltaDeg());
		activeSpinSpeed = step.newSpeed();
		if (step.shouldStop()) {
			spinning = false;
			activeSpinSpeed = 0;
		}
		return step;
	}

	/** Stops any in-flight spin. Idempotent. */
	public void stopSpin() {
		spinning = false;
		activeSpinSpeed = 0;
	}
}
