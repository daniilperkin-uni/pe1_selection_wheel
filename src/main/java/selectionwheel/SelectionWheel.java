package selectionwheel;

import java.awt.Color;
import java.awt.Font;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;


@SuppressWarnings("serial")
public class SelectionWheel extends JPanel {

	Wheel _wheel;
	Tick _tick;

	@Override
	public void setBounds(int x, int y, int width, int height) {
		/*
		 * Adjust the bounds of the wheel and tick based on tick width.
		 */
		super.setBounds(x, y, width, height);
		_wheel.setBounds(0, 0, width - _tick.getTickWidth(), height);
		_tick.setBounds(width - _tick.getTickWidth(), 0, _tick.getTickWidth(), height);
	}

	public void hasBorders(boolean borders) {
		/*
		 * Check if the wheel borders are on.
		 */
		_wheel.hasBorders(borders);
	}

	public int getRadius() {
		/*
		 * Get radius of the wheel.
		 */
		return _wheel.getRadius();
	}

	public double getRotationAngle() {
		/*
		 * Get current rotation angle of the wheel.
		 */
		return _wheel.getRotationAngle();
	}

	public void setRotationAngle(double rotationAngle) {
		/*
		 * Set current rotation angle of the wheel.
		 */
		_wheel.setRotationAngle(rotationAngle);
	}

	public Font getWheelFont() {
		/*
		 * Get current font of the wheel.
		 */
		return _wheel.getFont();
	}

	public void setWheelFont(Font font) {
		/*
		 * Set current font of the wheel.
		 */
		super.setFont(font);
		_wheel.setFont(font);
	}

	public ArrayList<String> getListOfStrings() {
		/*
		 * Get the list of strings for the wheel.
		 */
		return new ArrayList<>(_wheel.getListOfStrings());
	}

	public void setListOfStrings(ArrayList<String> list) {
		/*
		 * Set the list of strings for the wheel.
		 */
		_wheel.setListOfStrings(list);
	}

	public double getSpinSpeed() {
		/*
		 * Get current spin speed of the wheel.
		 */
		return _wheel.getSpinSpeed();
	}

	public double getMaxSpinSpeed() {
		/*
		 * Get current spin speed limit of the wheel.
		 */
		return _wheel.getMaxSpinSpeed();
	}

	public void setMaxSpinSpeed(double speed) {
		/*
		 * Set current spin speed limit of the wheel.
		 */
		_wheel.setMaxSpinSpeed(speed);
	}

	public double getSpinDeceleration() {
		return _wheel.getSpinDeceleration();
	}

	public void setSpinDeceleration(double deceleration) {
		_wheel.setSpinDeceleration(deceleration);
	}

	public ArrayList<Color> getColorScheme() {
		/*
		 * Get color scheme of the wheel.
		 */
		List<Color> colors = _wheel.getColorScheme();
		return colors == null ? null : new ArrayList<>(colors);
	}

	public void setColorScheme(ArrayList<Color> colors) {
		/*
		 * Set color scheme of the wheel.
		 */
		_wheel.setColorScheme(colors);
	}
	public void addColor(Color color) {
		/*
		 * Add new color to the color scheme of the wheel.
		 */
		_wheel.addColor(color);
	}

	public String getSelectedString() {
		/*
		 * Get current string selection for the wheel.
		 */
		return _wheel.getSelectedString();
	}

	public boolean isSpinning() {
		/*
		 * Check if wheel is spinning.
		 */
		return _wheel.isSpinning();
	}

	public void setShape(Wheel.Shape shape) {
		/*
		 * Set shape of the wheel.
		 */
		_wheel.setShape(shape);
	}

	public double getTickWidth() {
		/*
		 * Get tick width.
		 */
		return _tick.getTickWidth();
	}

	public void setTickWidth(int width) {
		/*
		 * Set tick width. Resets the bounds of both tick and wheel.
		 */
		_tick.setTickWidth(width);
		this.setBounds(this.getX(), this.getY(), this.getWidth(), this.getHeight());
	}

	public double getTickHeight() {
		/*
		 * Get tick height.
		 */
		return _tick.getTickHeight();
	}

	public void setTickHeight(int height) {
		/*
		 * Set tick height.
		 */
		_tick.setTickHeight(height);
	}

	public Polygon getTickPolygon() {
		/*
		 * Get tick polygon.
		 */
		return _tick.getPolygon();
	}

	public void setTickPolygon(Polygon polygon) {
		/*
		 * Set tick polygon.
		 */
		_tick.setPolygon(polygon);
	}

	public SelectionWheel(ArrayList<String> listOfStrings) {
		/*
		 * Constructor - initializes tick and wheel.
		 */
		_wheel = new Wheel(listOfStrings);
		_wheel.setLayout(null);
		_tick = new Tick();
		_tick.setLayout(null);
		this.setLayout(null);
		this.add(_wheel);
		this.add(_tick);
	}

	public void spinStartAsync(double speed, int direction, double deceleration){
		/*
		 * Start async wheel spin.
		 */

		_wheel.spinStartAsync(speed, direction, deceleration);
	}

	public void spinStop() {
		/*
		 * Stop spinning.
		 */
		_wheel.spinStop();
	}

	public void addWheelListener(WheelListener listener) {
		/*
		 * Register a WheelListener on the underlying wheel.
		 */
		_wheel.addWheelListener(listener);
	}

	public void removeWheelListener(WheelListener listener) {
		/*
		 * Remove a previously registered WheelListener.
		 */
		_wheel.removeWheelListener(listener);
	}
}
