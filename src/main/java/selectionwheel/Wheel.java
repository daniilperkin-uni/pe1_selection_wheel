package selectionwheel;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * Visual component that renders a circular (or umbrella-shaped) wheel
 * divided into labeled sections. The wheel can be rotated by dragging
 * the mouse and spun with a decelerating angular velocity.
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li>{@link WheelModel} holds the wheel's pure state: items, rotation
 *       angle, spin parameters. All math goes through {@link WheelMath}
 *       and spin physics through {@link SpinStep}, both of which are
 *       pure functions.</li>
 *   <li>This class ({@code Wheel}) is the view: a {@link JPanel} that
 *       paints the model and routes mouse/keyboard events into model
 *       mutations. It owns no domain logic.</li>
 *   <li>State mutations triggered by the user (mouse drag, release)
 *       call into the model via the public mutators, then repaint and
 *       fire {@link WheelListener} events on the EDT.</li>
 * </ul>
 *
 * <h2>Threading contract</h2>
 * All public mutators must be invoked on the Swing EDT.
 * {@link WheelListener} callbacks are also delivered on the EDT.
 */
@SuppressWarnings("serial")
public class Wheel extends JPanel {

	public enum Shape {
		CIRCLE,
		UMBRELLA
	}

	/** Pure state holder. All math goes through {@link WheelMath} / {@link SpinStep}. */
	private final WheelModel model;

	// ----- render cache (synced from model on mutation) -----
	private volatile Image image = null;
	private boolean hasBorders = false;
	private Point2D imagePosition;
	private Point2D rotationCenter;
	private double zoomFactor = 1;

	private List<Color> colors;
	int colorCounter = 0;

	private Shape shape = Shape.CIRCLE;
	private int radius;
	private Point2D center = new Point2D.Double();

	private final Font DEFAULTFONT = new Font("TimesRoman", Font.PLAIN, 12);
	private Font font = DEFAULTFONT;

	private long timeStart;
	private double rotationAngleStart;
	private Point2D mouseDragPosition;

	/** Drives the decelerating spin on the EDT. Replaces the raw Thread + Thread.sleep loop. */
	private Timer spinTimer = null;

	/** Last item reported via {@link WheelListener#selectionChanged} - dedupes events. */
	private String lastReportedSelection = null;

	private final List<WheelListener> listeners = new ArrayList<>();

	public Wheel(List<String> listOfStrings) {
		/*
		 * Constructor of the class.
		 * Sets the model, adds mouse listeners.
		 */
		model = new WheelModel(listOfStrings);

		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				mouseDragPosition = new Point2D.Double(e.getX(), e.getY());
				// to stop the spinning if the circle is clicked on
				double distance = Math.sqrt(
						Math.pow(mouseDragPosition.getX() - center.getX(), 2)
								+ Math.pow(mouseDragPosition.getY() - center.getY(), 2));
				if (distance <= radius) {
					spinStop();
				}
				// to measure initial speed
				timeStart = System.currentTimeMillis();
				rotationAngleStart = model.getRotationAngleDeg();
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
				// to measure initial speed
				long timeEnd = System.currentTimeMillis();
				double initialSpeed = WheelMath.computeInitialSpeedDegPerSec(
						rotationAngleStart, model.getRotationAngleDeg(),
						timeStart, timeEnd, model.getMaxSpinSpeedDegPerSec());
				if (Math.abs(initialSpeed) > 0) {
					spinStartAsync(Math.abs(initialSpeed), (int) Math.signum(initialSpeed),
							model.getSpinDeceleration());
				}
			}
		});

		addMouseMotionListener(new MouseAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				setCursor(new Cursor(Cursor.HAND_CURSOR));
				spinStop();
				/*
				 * Use the equation for angle between two vectors:
				 * vector 1 between last position of mouse and center of circle
				 * vector 2 between current position of mouse and center of circle
				 * ("k" is direction coefficient)
				 */
				// Guard against drag before the first paint: rotationCenter and
				// mouseDragPosition are only populated in paintComponent /
				// mousePressed respectively. Ignore drags until both are set.
				if (rotationCenter == null || mouseDragPosition == null) {
					return;
				}
				Point2D mousePos = new Point2D.Double(e.getX(), e.getY());
				double delta = WheelMath.dragDeltaDeg(mouseDragPosition, mousePos, rotationCenter);
				if (delta != 0) {
					setRotationAngle(getRotationAngle() + delta);
				}
				mouseDragPosition = mousePos;
			}
		});
	}

	@Override
	public void setBounds(int x, int y, int width, int height) {
		image = null;
		super.setBounds(x, y, width, height);
	}

	public void hasBorders(boolean borders) {
		/*
		 * Borders on/off.
		 * If switched on, borders of sections and circle + circle center will be visible.
		 */
		hasBorders = borders;
		image = null;
		spinStop();
		setRotationAngle(0);
		this.repaint();
	}

	public void setShape(Shape shape) {
		/*
		 * Set the shape of the wheel.
		 * Options in Shape enum.
		 */
		this.shape = shape;
		image = null;
		spinStop();
		setRotationAngle(0);
		this.repaint();
	}

	public double getRotationAngle() {
		/*
		 * Get current rotation of the wheel.
		 */
		return model.getRotationAngleDeg();
	}

	public void setRotationAngle(double rotationAngle) {
		/*
		 * Set the current rotation of the wheel.
		 */
		model.setRotationAngleDeg(rotationAngle);
		this.repaint();
		fireRotationChanged(model.getRotationAngleDeg());
		fireSelectionChangedMaybe();
	}

	public List<Color> getColorScheme() {
		/*
		 * Get List of colors used for sections of the wheel.
		 */
		return colors;
	}

	public void setColorScheme(List<Color> colors) {
		/*
		 * Set List of colors used for sections of the wheel.
		 */
		this.colors = colors;
		image = null;
		spinStop();
		setRotationAngle(0);
		this.repaint();
	}

	public void addColor(Color color) {
		/*
		 * Add a new color to the existing color scheme for the sections of the wheel.
		 */
		if (colors == null)
			colors = new ArrayList<>();
		colors.add(color);
		image = null;
		spinStop();
		setRotationAngle(0);
		this.repaint();
	}

	public int getRadius() {
		/*
		 * Get radius of the wheel.
		 * The radius is set in DrawImage method based on the dimensions of this.
		 */
		return radius;
	}

	public List<String> getItems() {
		/*
		 * Get the list of items displayed inside the sections of the wheel.
		 */
		return model.getItems();
	}

	public void setItems(List<String> list) {
		/*
		 * Set the list of items displayed inside the sections of the wheel.
		 * The initial list is set in constructor method and can be changed during runtime.
		 */
		model.setItems(list);
		image = null;
		spinStop();
		setRotationAngle(0);
		this.repaint();
	}

	@Override
	public Font getFont() {
		/*
		 * Get current font of the displayed strings in the wheel.
		 */
		return font;
	}

	@Override
	public void setFont(Font font) {
		/*
		 * Set current font of the displayed strings in the wheel.
		 */
		super.setFont(font);
		this.font = font;
		// Guard against virtual method call during JPanel construction:
		// JPanel's constructor calls updateUI() -> setFont() before our
		// model field is initialized. Skip the wheel-specific logic in
		// that case; it will run again when setItems or another
		// mutator is called later.
		if (model == null) return;
		image = null;
		spinStop();
		setRotationAngle(0);
		this.repaint();
	}

	public double getSpinSpeed() {
		/*
		 * Get current spinning speed in degrees per second.
		 * If the spinning is off, it returns 0.
		 */
		return model.getSpinSpeedDegPerSec();
	}

	public double getMaxSpinSpeed() {
		/*
		 * Get current speed limit.
		 */
		return model.getMaxSpinSpeedDegPerSec();
	}

	public void setMaxSpinSpeed(double speed) {
		/*
		 * Set current speed limit.
		 */
		spinStop();
		model.setMaxSpinSpeedDegPerSec(speed);
	}

	public double getSpinDeceleration() {
		return model.getSpinDeceleration();
	}

	public void setSpinDeceleration(double deceleration) {
		model.setSpinDeceleration(deceleration);
	}

	public boolean isSpinning() {
		/*
		 * Check if the wheel is spinning.
		 */
		return model.isSpinning();
	}

	public String getSelectedItem() {
		/*
		 * Get current selection.
		 * Returns the string which is displayed in the section of the wheel
		 * that is currently positioned between 0 and delta degrees.
		 */
		return model.getSelectedItem();
	}

	@Override
	public void paintComponent(Graphics g) {
		/*
		 * Paintcomponent - if the image is null, create it and then draw it
		 * whilst keeping the current rotation. The image can be larger than
		 * the displaying area, so after it is drawn it needs to be placed
		 * properly.
		 */
		super.paintComponent(g);

		// Guard against paint during construction (before model is set).
		if (model == null) return;

		if (image == null) {
			image = drawImage();
			rotationCenter = new Point2D.Double(
					this.getWidth() - image.getWidth(null) + center.getX(),
					this.getHeight() / 2);
			imagePosition = new Point2D.Double(
					(int) (this.getWidth() - image.getWidth(null)),
					(int) (this.getHeight() / 2 - center.getY()));
		}

		Graphics2D gPanel = (Graphics2D) g;
		gPanel.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		gPanel.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		gPanel.rotate(Math.toRadians(model.getRotationAngleDeg()),
				rotationCenter.getX(), rotationCenter.getY());
		gPanel.drawImage(image, (int) imagePosition.getX(), (int) imagePosition.getY(), null);
	}

	private BufferedImage drawImage() {
		if (colors == null)
			colors = getDefaultColorList();
		WheelRenderer r = new WheelRenderer();
		r.font = font;
		r.colors = colors;
		r.shape = shape;
		r.hasBorders = hasBorders;
		r.zoomFactor = zoomFactor;
		BufferedImage img = r.render(model, getWidth(), getHeight());
		radius = r.radius;
		center = r.center;
		zoomFactor = r.zoomFactor;
		colorCounter = model.getNumSections();
		return img;
	}

	/** Spin animation refresh rate in frames per second. */
	private static final int REFRESH_RATE = 100;

	/**
	 * Starts an asynchronous decelerating spin.
	 *
	 * <p>Safe to call from any thread; non-EDT callers are marshalled to
	 * the EDT via {@link SwingUtilities#invokeLater}.
	 *
	 * @param speed        initial angular speed in degrees per second ({@code >= 0})
	 * @param direction    {@code +1} for counter-clockwise, {@code -1} for clockwise,
	 *                     {@code 0} to start a stopped spin (no-op)
	 * @param deceleration {@code <= 0}; degrees per second per second
	 * @throws IllegalArgumentException if {@code deceleration > 0}
	 */
	public void spinStartAsync(double speed, int direction, double deceleration) {
		if (deceleration > 0) {
			throw new IllegalArgumentException(
					"deceleration must be <= 0 (was " + deceleration + ")");
		}
		if (speed <= 0 || direction == 0) {
			// No-op spin: nothing to animate.
			return;
		}
		Runnable start = () -> doStartSpin(speed, direction, deceleration);
		if (EventQueue.isDispatchThread()) {
			start.run();
		} else {
			SwingUtilities.invokeLater(start);
		}
	}

	private void doStartSpin(double speed, int direction, double deceleration) {
		// Stop any in-flight spin before starting a new one.
		stopSpinTimer();
		model.startSpin(speed, direction, deceleration);
		int intervalMs = 1000 / REFRESH_RATE;
		spinTimer = new Timer(intervalMs, e -> onSpinTick());
		spinTimer.setRepeats(true);
		spinTimer.start();
		fireSpinStarted();
	}

	/**
	 * Called on every tick of {@link #spinTimer}. Delegates the
	 * physics step to the model, then fires events and repaints.
	 */
	private void onSpinTick() {
		SpinStep step = model.tickSpin(1.0 / REFRESH_RATE);
		fireRotationChanged(model.getRotationAngleDeg());
		fireSelectionChangedMaybe();
		if (step.shouldStop()) {
			stopSpinTimer();
			fireSpinStopped();
		} else {
			fireSpinSpeedChanged(model.getSpinSpeedDegPerSec());
		}
		repaint();
	}

	/**
	 * Stops any in-flight spin and stops the timer cleanly. Safe to call
	 * from any thread; non-EDT callers are marshalled to the EDT.
	 */
	public void spinStop() {
		Runnable stop = () -> {
			boolean wasSpinning = model.isSpinning();
			stopSpinTimer();
			model.stopSpin();
			if (wasSpinning) {
				fireSpinStopped();
			}
		};
		if (EventQueue.isDispatchThread()) {
			stop.run();
		} else {
			SwingUtilities.invokeLater(stop);
		}
	}

	private void stopSpinTimer() {
		if (spinTimer != null) {
			spinTimer.stop();
			spinTimer = null;
		}
	}

	// ----- listener registration -----

	public void addWheelListener(WheelListener l) {
		if (l != null) listeners.add(l);
	}

	public void removeWheelListener(WheelListener l) {
		listeners.remove(l);
	}

	private void fireSpinStarted() {
		for (WheelListener l : listeners) l.spinStarted();
	}

	private void fireSpinStopped() {
		for (WheelListener l : listeners) l.spinStopped();
	}

	private void fireSpinSpeedChanged(double speed) {
		for (WheelListener l : listeners) l.spinSpeedChanged(speed);
	}

	private void fireRotationChanged(double angle) {
		for (WheelListener l : listeners) l.rotationChanged(angle);
	}

	private void fireSelectionChangedMaybe() {
		String current = model.getSelectedItem();
		if (current == null) return;
		if (!current.equals(lastReportedSelection)) {
			lastReportedSelection = current;
			for (WheelListener l : listeners) l.selectionChanged(current);
		}
	}

	private List<Color> getDefaultColorList() {
		/*
		 * Returns default color list.
		 * To be used in case when no explicit color list is set.
		 */
		List<Color> defaultColors = new ArrayList<>();
		defaultColors.add(Color.BLUE);
		defaultColors.add(Color.CYAN);
		defaultColors.add(Color.DARK_GRAY);
		defaultColors.add(Color.GRAY);
		defaultColors.add(Color.GREEN);
		defaultColors.add(Color.LIGHT_GRAY);
		defaultColors.add(Color.MAGENTA);
		defaultColors.add(Color.ORANGE);
		defaultColors.add(Color.PINK);
		defaultColors.add(Color.RED);
		defaultColors.add(Color.WHITE);
		defaultColors.add(Color.YELLOW);
		return defaultColors;
	}
}
