package selectionwheel;

import java.awt.Color;
import java.awt.Font;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;


@SuppressWarnings("serial")
public class SelectionWheel extends JPanel {

	Wheel wheel;
	Tick tick;
	private boolean tickVisible = true;

	/** Package-private accessor used by tests instead of direct field access. */
	Wheel getWheel() {
		return wheel;
	}

	/** Package-private accessor used by tests instead of direct field access. */
	Tick getTick() {
		return tick;
	}

	@Override
	public void setBounds(int x, int y, int width, int height) {
		/*
		 * Adjust the bounds of the wheel and tick based on tick width.
		 * If the tick is hidden, the wheel gets the full width.
		 */
		super.setBounds(x, y, width, height);
		if (tickVisible) {
			int tickWidth = tick.getTickWidth();
			wheel.setBounds(0, 0, width - tickWidth, height);
			tick.setBounds(width - tickWidth, 0, tickWidth, height);
		} else {
			wheel.setBounds(0, 0, width, height);
			tick.setBounds(0, 0, 0, 0);
		}
	}

	/**
	 * Controls whether the tick (pointer) is visible. When hidden, the
	 * wheel expands to fill the full bounds of this container.
	 *
	 * <p>Defaults to {@code true} for backward compatibility.
	 *
	 * @param visible {@code true} to show the tick, {@code false} to hide
	 */
	public void setTickVisible(boolean visible) {
		tickVisible = visible;
		tick.setVisible(visible);
		this.setBounds(this.getX(), this.getY(), this.getWidth(), this.getHeight());
	}

	public boolean isTickVisible() {
		return tickVisible;
	}

	public void hasBorders(boolean borders) {
		/*
		 * Check if the wheel borders are on.
		 */
		wheel.hasBorders(borders);
	}

	public int getRadius() {
		/*
		 * Get radius of the wheel.
		 */
		return wheel.getRadius();
	}

	public double getRotationAngle() {
		/*
		 * Get current rotation angle of the wheel.
		 */
		return wheel.getRotationAngle();
	}

	public void setRotationAngle(double rotationAngle) {
		/*
		 * Set current rotation angle of the wheel.
		 */
		wheel.setRotationAngle(rotationAngle);
	}

	public Font getWheelFont() {
		/*
		 * Get current font of the wheel.
		 */
		return wheel.getFont();
	}

	public void setWheelFont(Font font) {
		/*
		 * Set current font of the wheel.
		 */
		super.setFont(font);
		wheel.setFont(font);
	}

	public ArrayList<String> getItems() {
		/*
		 * Get the list of items for the wheel.
		 */
		return new ArrayList<>(wheel.getItems());
	}

	public void setItems(ArrayList<String> list) {
		/*
		 * Set the list of items for the wheel.
		 */
		wheel.setItems(list);
	}

	public double getSpinSpeed() {
		/*
		 * Get current spin speed of the wheel.
		 */
		return wheel.getSpinSpeed();
	}

	public double getMaxSpinSpeed() {
		/*
		 * Get current spin speed limit of the wheel.
		 */
		return wheel.getMaxSpinSpeed();
	}

	public void setMaxSpinSpeed(double speed) {
		/*
		 * Set current spin speed limit of the wheel.
		 */
		wheel.setMaxSpinSpeed(speed);
	}

	public double getSpinDeceleration() {
		return wheel.getSpinDeceleration();
	}

	public void setSpinDeceleration(double deceleration) {
		wheel.setSpinDeceleration(deceleration);
	}

	public ArrayList<Color> getColorScheme() {
		/*
		 * Get color scheme of the wheel.
		 */
		List<Color> colors = wheel.getColorScheme();
		return colors == null ? null : new ArrayList<>(colors);
	}

	public void setColorScheme(ArrayList<Color> colors) {
		/*
		 * Set color scheme of the wheel.
		 */
		wheel.setColorScheme(colors);
	}
	public void addColor(Color color) {
		/*
		 * Add new color to the color scheme of the wheel.
		 */
		wheel.addColor(color);
	}

	public String getSelectedItem() {
		/*
		 * Get current item selection for the wheel.
		 */
		return wheel.getSelectedItem();
	}

	public boolean isSpinning() {
		/*
		 * Check if wheel is spinning.
		 */
		return wheel.isSpinning();
	}

	public void setShape(Wheel.Shape shape) {
		/*
		 * Set shape of the wheel.
		 */
		wheel.setShape(shape);
	}

	public double getTickWidth() {
		/*
		 * Get tick width.
		 */
		return tick.getTickWidth();
	}

	public void setTickWidth(int width) {
		/*
		 * Set tick width. Resets the bounds of both tick and wheel.
		 */
		tick.setTickWidth(width);
		this.setBounds(this.getX(), this.getY(), this.getWidth(), this.getHeight());
	}

	public double getTickHeight() {
		/*
		 * Get tick height (informational; see Tick).
		 */
		return tick.getTickHeight();
	}

	public void setTickHeight(int height) {
		/*
		 * Set tick height (currently without visual effect; see Tick).
		 */
		tick.setTickHeight(height);
	}

	public Polygon getTickPolygon() {
		/*
		 * Get tick polygon.
		 */
		return tick.getPolygon();
	}

	public void setTickPolygon(Polygon polygon) {
		/*
		 * Set tick polygon.
		 */
		tick.setPolygon(polygon);
	}

	public SelectionWheel(ArrayList<String> listOfStrings) {
		/*
		 * Constructor - initializes tick and wheel.
		 */
		wheel = new Wheel(listOfStrings);
		wheel.setLayout(null);
		tick = new Tick();
		tick.setLayout(null);
		this.setLayout(null);
		this.add(wheel);
		this.add(tick);
	}

	public void spinStartAsync(double speed, int direction, double deceleration){
		/*
		 * Start async wheel spin.
		 */

		wheel.spinStartAsync(speed, direction, deceleration);
	}

	public void spinStop() {
		/*
		 * Stop spinning.
		 */
		wheel.spinStop();
	}

	public void addWheelListener(WheelListener listener) {
		/*
		 * Register a WheelListener on the underlying wheel.
		 */
		wheel.addWheelListener(listener);
	}

	public void removeWheelListener(WheelListener listener) {
		/*
		 * Remove a previously registered WheelListener.
		 */
		wheel.removeWheelListener(listener);
	}
}
