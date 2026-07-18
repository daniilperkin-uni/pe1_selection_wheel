package selectionwheel;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/**
 * Visual component that renders the "tick" (pointer) attached to a
 * {@link Wheel}. The pointer is the small triangle (or custom polygon)
 * at the right edge of the wheel that indicates the currently selected
 * section.
 *
 * <p>All polygon geometry is delegated to {@link TickMath}, which is
 * pure and unit-tested. Notably, {@link TickMath#adjustPolygon} returns
 * a fresh {@link Polygon} on each call, so repeated repaints no longer
 * compound scaling factors (the original in-place version did).
 */
@SuppressWarnings("serial")
public class Tick extends JPanel {

	/** Original polygon supplied by the user via {@link #setPolygon}. Never mutated. */
	private Polygon _polygon_orig = null;
	/** Current polygon to render, either the default triangle or an adjusted copy of {@link #_polygon_orig}. */
	private Polygon _polygon = null;

	private int _tickWidth = 20;
	private int _tickHeight = 20;

	public int getTickWidth() {
		/*
		 * Get tick width.
		 */
		return _tickWidth;
	}

	public void setTickWidth(int width) {
		/*
		 * Set tick width.
		 */
		_tickWidth = width;
	}

	public int getTickHeight() {
		/*
		 * Get tick height.
		 */
		return _tickHeight;
	}

	public void setTickHeight(int height) {
		/*
		 * Set tick height.
		 */
		_tickHeight = height;
	}

	public Polygon getPolygon() {
		/*
		 * Get the polygon shape of the tick as last rendered. May be null
		 * if {@link #paintComponent} has not yet been called.
		 */
		return _polygon;
	}

	public void setPolygon(Polygon polygon) {
		/*
		 * Set the custom polygon shape of the tick. The polygon is stored
		 * by reference but never mutated; on each repaint a fresh, scaled
		 * and centered copy is produced via {@link TickMath#adjustPolygon}.
		 */
		_polygon_orig = polygon;
		// Force recompute on next paint.
		_polygon = null;
		this.repaint();
	}

	public Tick() {
		super();
		this.repaint();
	}

	private Polygon getTriangle() {
		/*
		 * Get the default triangle polygon (pointing left into the wheel).
		 * Delegates to {@link TickMath} so the geometry is testable.
		 */
		return TickMath.computeDefaultTriangle(getWidth(), getHeight());
	}

	@Override
	public void paintComponent(Graphics g) {
		/*
		 * Paintcomponent.
		 * If a custom polygon is set, scale and center it via TickMath;
		 * otherwise use the default triangle.
		 */
		super.paintComponent(g);
		Graphics2D g2d = (Graphics2D) g;
		RenderingHints rh = new RenderingHints(
				RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.addRenderingHints(rh);

		if (_polygon_orig == null) {
			_polygon = getTriangle();
		} else {
			// TickMath.adjustPolygon returns a fresh polygon - never mutates
			// _polygon_orig, so repeated repaints are idempotent.
			_polygon = TickMath.adjustPolygon(_polygon_orig, getWidth(), getHeight());
		}
		g2d.fillPolygon(_polygon);
	}
}
