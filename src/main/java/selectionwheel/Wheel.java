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
	private final WheelModel _model;

	// ----- render cache (synced from _model on mutation) -----
	private volatile Image _image = null;
	private boolean _hasBorders = false;
	private Point2D _imagePosition;
	private Point2D _rotationCenter;
	private double _zoomFactor = 1;

	private List<Color> _colors;
	int _colorCounter = 0;

	private Shape _shape = Shape.CIRCLE;
	private final int BORDER = 10;
	private int _radius;
	private Point2D _center = new Point2D.Double();

	private final Font DEFAULTFONT = new Font("TimesRoman", Font.PLAIN, 12);
	private Font _font = DEFAULTFONT;

	private long _timeStart;
	private double _rotationAngleStart;
	private Point2D _mouseDragPosition;

	/** Drives the decelerating spin on the EDT. Replaces the raw Thread + Thread.sleep loop. */
	private Timer _spinTimer = null;

	/** Last item reported via {@link WheelListener#selectionChanged} - dedupes events. */
	private String _lastReportedSelection = null;

	private final List<WheelListener> _listeners = new ArrayList<>();

	public Wheel(List<String> listOfStrings) {
		/*
		 * Constructor of the class.
		 * Sets the model, adds mouse listeners.
		 */
		_model = new WheelModel(listOfStrings);

		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				_mouseDragPosition = new Point2D.Double(e.getX(), e.getY());
				// to stop the spinning if the circle is clicked on
				double distance = Math.sqrt(
						Math.pow(_mouseDragPosition.getX() - _center.getX(), 2)
								+ Math.pow(_mouseDragPosition.getY() - _center.getY(), 2));
				if (distance <= _radius) {
					spinStop();
				}
				// to measure initial speed
				_timeStart = System.currentTimeMillis();
				_rotationAngleStart = _model.getRotationAngleDeg();
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
				// to measure initial speed
				long timeEnd = System.currentTimeMillis();
				double initialSpeed = WheelMath.computeInitialSpeedDegPerSec(
						_rotationAngleStart, _model.getRotationAngleDeg(),
						_timeStart, timeEnd, _model.getMaxSpinSpeedDegPerSec());
				if (Math.abs(initialSpeed) > 0) {
					spinStartAsync(Math.abs(initialSpeed), (int) Math.signum(initialSpeed),
							_model.getSpinDeceleration());
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
				Point2D mousePos = new Point2D.Double(e.getX(), e.getY());
				double delta = WheelMath.dragDeltaDeg(_mouseDragPosition, mousePos, _rotationCenter);
				if (delta != 0) {
					setRotationAngle(getRotationAngle() + delta);
				}
				_mouseDragPosition = mousePos;
			}
		});
	}

	@Override
	public void setBounds(int x, int y, int width, int height) {
		_image = null;
		super.setBounds(x, y, width, height);
	}

	public void hasBorders(boolean borders) {
		/*
		 * Borders on/off.
		 * If switched on, borders of sections and circle + circle center will be visible.
		 */
		_hasBorders = borders;
		_image = null;
		spinStop();
		setRotationAngle(0);
		this.repaint();
	}

	public void setShape(Shape shape) {
		/*
		 * Set the shape of the wheel.
		 * Options in Shape enum.
		 */
		_shape = shape;
		_image = null;
		spinStop();
		setRotationAngle(0);
		this.repaint();
	}

	public double getRotationAngle() {
		/*
		 * Get current rotation of the wheel.
		 */
		return _model.getRotationAngleDeg();
	}

	public void setRotationAngle(double rotationAngle) {
		/*
		 * Set the current rotation of the wheel.
		 */
		_model.setRotationAngleDeg(rotationAngle);
		this.repaint();
		fireRotationChanged(_model.getRotationAngleDeg());
		fireSelectionChangedMaybe();
	}

	public List<Color> getColorScheme() {
		/*
		 * Get List of colors used for sections of the wheel.
		 */
		return _colors;
	}

	public void setColorScheme(List<Color> colors) {
		/*
		 * Set List of colors used for sections of the wheel.
		 */
		_colors = colors;
		_image = null;
		spinStop();
		setRotationAngle(0);
		this.repaint();
	}

	public void addColor(Color color) {
		/*
		 * Add a new color to the existing color scheme for the sections of the wheel.
		 */
		if (_colors == null)
			_colors = new ArrayList<>();
		_colors.add(color);
		_image = null;
		spinStop();
		setRotationAngle(0);
		this.repaint();
	}

	public int getRadius() {
		/*
		 * Get radius of the wheel.
		 * The radius is set in DrawImage method based on the dimensions of this.
		 */
		return _radius;
	}

	public List<String> getListOfStrings() {
		/*
		 * Get list of strings displayed inside the sections of the wheel.
		 */
		return _model.getItems();
	}

	public void setListOfStrings(List<String> list) {
		/*
		 * Set list of strings displayed inside the sections of the wheel.
		 * The initial list is set in constructor method and can be changed during runtime.
		 */
		_model.setItems(list);
		_image = null;
		spinStop();
		setRotationAngle(0);
		this.repaint();
	}

	@Override
	public Font getFont() {
		/*
		 * Get current font of the displayed strings in the wheel.
		 */
		return _font;
	}

	@Override
	public void setFont(Font font) {
		/*
		 * Set current font of the displayed strings in the wheel.
		 */
		super.setFont(font);
		_font = font;
		_image = null;
		spinStop();
		setRotationAngle(0);
		this.repaint();
	}

	public double getSpinSpeed() {
		/*
		 * Get current spinning speed in degrees per second.
		 * If the spinning is off, it returns 0.
		 */
		return _model.getSpinSpeedDegPerSec();
	}

	public double getMaxSpinSpeed() {
		/*
		 * Get current speed limit.
		 */
		return _model.getMaxSpinSpeedDegPerSec();
	}

	public void setMaxSpinSpeed(double speed) {
		/*
		 * Set current speed limit.
		 */
		spinStop();
		_model.setMaxSpinSpeedDegPerSec(speed);
	}

	public double getSpinDeceleration() {
		return _model.getSpinDeceleration();
	}

	public void setSpinDeceleration(double deceleration) {
		_model.setSpinDeceleration(deceleration);
	}

	public boolean isSpinning() {
		/*
		 * Check if the wheel is spinning.
		 */
		return _model.isSpinning();
	}

	public String getSelectedString() {
		/*
		 * Get current selection.
		 * Returns the string which is displayed in the section of the wheel
		 * that is currently positioned between 0 and delta degrees.
		 */
		return _model.getSelectedItem();
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

		if (_image == null) {
			_image = drawImage();
			_rotationCenter = new Point2D.Double(
					this.getWidth() - _image.getWidth(null) + _center.getX(),
					this.getHeight() / 2);
			_imagePosition = new Point2D.Double(
					(int) (this.getWidth() - _image.getWidth(null)),
					(int) (this.getHeight() / 2 - _center.getY()));
		}

		Graphics2D gPanel = (Graphics2D) g;
		gPanel.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		gPanel.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		gPanel.rotate(Math.toRadians(_model.getRotationAngleDeg()),
				_rotationCenter.getX(), _rotationCenter.getY());
		gPanel.drawImage(_image, (int) _imagePosition.getX(), (int) _imagePosition.getY(), null);
	}

	private BufferedImage drawImage() {
		/*
		 * Calculate all the necessary parameters for the wheel and draw it
		 * section by section.
		 */
		List<String> stringList = _model.getItems();
		int noElem = _model.getNumSections();
		double delta = _model.getSectionAngleDeg();
		double rotationAngle = _model.getRotationAngleDeg();

		int width = this.getWidth(), height = this.getHeight();
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = (Graphics2D) img.getGraphics();

		// Calculate radius
		_radius = Math.min(img.getWidth(), img.getHeight()) / 2 - BORDER;

		double stringDistanceFromEdge = 0.05 * _radius;
		int fontSize, stringWidth, maxStringWidth;

		maxStringWidth = (int) (_radius - 2 * stringDistanceFromEdge);
		fontSize = calcFontSize(g2d, stringDistanceFromEdge, maxStringWidth, stringList, noElem, delta);
		g2d.setFont(new Font(_font.getFamily(), _font.getStyle(), fontSize));

		// Adjust the parameters (for "zoom in") - if the font size is too small
		if (fontSize < MINFONTSIZE) {
			_zoomFactor = (double) MINFONTSIZE / fontSize;
			width += (int) 2 * ((_zoomFactor * _radius) - _radius);
			height += (int) 2 * ((_zoomFactor * _radius) - _radius);
			_radius = (int) (_zoomFactor * _radius);
			img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			g2d = (Graphics2D) img.getGraphics();
			maxStringWidth = (int) (_radius - 2 * stringDistanceFromEdge);
			fontSize = calcFontSize(g2d, stringDistanceFromEdge, maxStringWidth, stringList, noElem, delta);
		}

		// Calculate center point
		_center = new Point2D.Double((double) img.getWidth() / 2, (double) img.getHeight() / 2);

		// Set rendering hints
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2d.rotate(Math.toRadians(rotationAngle), _center.getX(), _center.getY());

		// Draw center point
		if (_hasBorders) {
			g2d.setColor(Color.BLACK);
			g2d.fillArc(
					(int) _center.getX() - (int) Math.floor(Math.max(0.01 * _radius, 1)),
					(int) _center.getY() - (int) Math.floor(Math.max(0.01 * _radius, 1)),
					(int) Math.floor(Math.max(0.01 * 2 * _radius, 2)),
					(int) Math.floor(Math.max(0.01 * 2 * _radius, 2)),
					0, 360);
		}

		// Divide circle and draw strings
		FontMetrics fontMetrics;
		if (_colors == null)
			_colors = getDefaultColorList();
		_colorCounter = 0;
		for (int i = noElem - 1; i >= 0; i--) {
			// Draw section border
			if (_hasBorders) {
				g2d.setColor(Color.BLACK);
				g2d.drawLine((int) _center.getX(), (int) _center.getY(),
						(int) _center.getX() + _radius, (int) _center.getY());
			}
			// Fill section depending on the chosen shape
			g2d.setColor(_colors.get(_colorCounter++ % _colors.size()));
			if (_shape == Shape.UMBRELLA)
				fillTriangle(g2d, delta);
			else //if(_shape == Shape.CIRCLE)
				fillArc(g2d, delta);
			// Draw string - rotate half delta, then draw then rotate the other half
			// (to have the string in the middle of the section)
			g2d.rotate(Math.toRadians(delta / 2), _center.getX(), _center.getY());
			g2d.setColor(Color.BLACK);
			fontMetrics = g2d.getFontMetrics();
			stringWidth = fontMetrics.stringWidth(stringList.get(i));
			g2d.drawString(stringList.get(i),
					(int) (_center.getX() + maxStringWidth - stringWidth + stringDistanceFromEdge),
					(int) (_center.getY() + (double) fontMetrics.getHeight() / 2
							- fontMetrics.getMaxDescent()));
			g2d.rotate(Math.toRadians(delta / 2), _center.getX(), _center.getY());
		}

		return img;
	}

	private static final int MAXFONTSIZE = 80;
	private static final int MINFONTSIZE = 10;
	private static final int LIMIT = 100;

	private int calcFontSize(Graphics g, double stringDistanceFromEdge, int maxStringWidth,
			List<String> stringList, int noElem, double delta) {
		/*
		 * Calculates the optimal font size for the strings inside the sections.
		 * The strings need to be positioned next to the broader end of the section.
		 * The optimal size will depend on the longest string length and maximum
		 * height of the section in the left border of the rectangle surrounding
		 * the string.
		 */

		// Find the longest string
		String tmpString = "";
		for (int i = noElem - 1; i >= 0; i--) {
			if (stringList.get(i).length() > tmpString.length())
				tmpString = stringList.get(i);
		}

		// Set it to max font size and calculate rectangle
		int fontSize = MAXFONTSIZE;
		g.setFont(new Font(_font.getFamily(), _font.getStyle(), fontSize));
		FontMetrics fontMetrics = g.getFontMetrics();
		Rectangle2D stringBounds = fontMetrics.getStringBounds(tmpString, g);

		// Adjust string height / font size
		int maxHeight = (int) Math.floor(2 * stringDistanceFromEdge * Math.sin(Math.toRadians(delta / 2)));
		if (stringBounds.getHeight() > maxHeight) {
			fontSize = (int) Math.floor(fontSize * maxHeight / stringBounds.getHeight());
			g.setFont(new Font(_font.getFamily(), _font.getStyle(), fontSize));
			fontMetrics = g.getFontMetrics();
			stringBounds = fontMetrics.getStringBounds(tmpString, g);
		}

		// Adjust string width
		// If the string is too narrow, increase font until it fits
		double K = stringBounds.getWidth() / stringBounds.getHeight();
		maxHeight = (int) Math.floor(2 * (_radius - stringDistanceFromEdge) * Math.tan(Math.toRadians(delta / 2))
				/ (1 + 2 * K * Math.tan(Math.toRadians(delta / 2))));
		while (stringBounds.getWidth() < maxStringWidth) {
			g.setFont(new Font(_font.getFamily(), _font.getStyle(), ++fontSize));
			fontMetrics = g.getFontMetrics();
			stringBounds = fontMetrics.getStringBounds(tmpString, g);
		}
		// If the string is too wide, decrease font until it fits
		while (stringBounds.getWidth() > maxStringWidth) {
			g.setFont(new Font(_font.getFamily(), _font.getStyle(), --fontSize));
			fontMetrics = g.getFontMetrics();
			stringBounds = fontMetrics.getStringBounds(tmpString, g);
		}

		return Math.min(fontSize, MAXFONTSIZE);
	}

	private void fillArc(Graphics g2d, double delta) {
		g2d.fillArc((int) _center.getX() - _radius, (int) _center.getY() - _radius,
				2 * _radius, 2 * _radius, 0, (int) -Math.ceil(delta));
		// use ceil because of decimal part (would be left empty)
		if (_hasBorders) {
			g2d.setColor(Color.black);
			g2d.drawArc((int) _center.getX() - _radius, (int) _center.getY() - _radius,
					2 * _radius, 2 * _radius, 0, (int) -Math.ceil(delta));
		}
	}

	private void fillTriangle(Graphics2D g2d, double delta) {
		/*
		 * Method that draws section as a triangle (in case Shape=UMBRELLA was chosen)
		 */
		int[] xpoints = new int[3];
		xpoints[0] = (int) _center.getX();
		xpoints[1] = (int) _center.getX() + _radius;
		int dx = (int) (2 * _radius * Math.pow(Math.sin(Math.toRadians(delta / 2)), 2));
		xpoints[2] = xpoints[1] - dx;
		int[] ypoints = new int[3];
		ypoints[0] = (int) _center.getY();
		ypoints[1] = (int) _center.getY();
		int dy = (int) (2 * _radius * Math.sin(Math.toRadians(delta / 2)) * Math.cos(Math.toRadians(delta / 2)));
		ypoints[2] = ypoints[1] + dy;
		g2d.fillPolygon(xpoints, ypoints, 3);
		if (_hasBorders) {
			g2d.setColor(Color.black);
			g2d.drawLine(xpoints[1], ypoints[1], xpoints[2], ypoints[2]);
		}
	}

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
		_model.startSpin(speed, direction, deceleration);
		int intervalMs = 1000 / REFRESH_RATE;
		_spinTimer = new Timer(intervalMs, e -> onSpinTick());
		_spinTimer.setRepeats(true);
		_spinTimer.start();
		fireSpinStarted();
	}

	private static final int REFRESH_RATE = 100;

	/**
	 * Called on every tick of {@link #_spinTimer}. Delegates the
	 * physics step to the model, then fires events and repaints.
	 */
	private void onSpinTick() {
		SpinStep step = _model.tickSpin(1.0 / REFRESH_RATE);
		fireRotationChanged(_model.getRotationAngleDeg());
		fireSelectionChangedMaybe();
		if (step.shouldStop()) {
			stopSpinTimer();
			fireSpinStopped();
		} else {
			fireSpinSpeedChanged(_model.getSpinSpeedDegPerSec());
		}
		repaint();
	}

	/**
	 * Stops any in-flight spin and stops the timer cleanly. Safe to call
	 * from any thread; non-EDT callers are marshalled to the EDT.
	 */
	public void spinStop() {
		Runnable stop = () -> {
			boolean wasSpinning = _model.isSpinning();
			stopSpinTimer();
			_model.stopSpin();
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
		if (_spinTimer != null) {
			_spinTimer.stop();
			_spinTimer = null;
		}
	}

	// ----- listener registration -----

	public void addWheelListener(WheelListener l) {
		if (l != null) _listeners.add(l);
	}

	public void removeWheelListener(WheelListener l) {
		_listeners.remove(l);
	}

	private void fireSpinStarted() {
		for (WheelListener l : _listeners) l.spinStarted();
	}

	private void fireSpinStopped() {
		for (WheelListener l : _listeners) l.spinStopped();
	}

	private void fireSpinSpeedChanged(double speed) {
		for (WheelListener l : _listeners) l.spinSpeedChanged(speed);
	}

	private void fireRotationChanged(double angle) {
		for (WheelListener l : _listeners) l.rotationChanged(angle);
	}

	private void fireSelectionChangedMaybe() {
		String current = _model.getSelectedItem();
		if (current == null) return;
		if (!current.equals(_lastReportedSelection)) {
			_lastReportedSelection = current;
			for (WheelListener l : _listeners) l.selectionChanged(current);
		}
	}

	private List<Color> getDefaultColorList() {
		/*
		 * Returns default color list.
		 * To be used in case when no explicit color list is set.
		 */
		List<Color> colors = new ArrayList<>();
		colors.add(Color.BLUE);
		colors.add(Color.CYAN);
		colors.add(Color.DARK_GRAY);
		colors.add(Color.GRAY);
		colors.add(Color.GREEN);
		colors.add(Color.LIGHT_GRAY);
		colors.add(Color.MAGENTA);
		colors.add(Color.ORANGE);
		colors.add(Color.PINK);
		colors.add(Color.RED);
		colors.add(Color.WHITE);
		colors.add(Color.YELLOW);
		return colors;
	}
}
