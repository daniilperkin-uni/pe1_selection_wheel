package selectionwheel;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Rasterizes a wheel (sections, borders, labels) into a BufferedImage.
 * Extracted from {@link Wheel}; pure rendering, no Swing state. Layout
 * results of the last render (radius, center, zoomFactor) are exposed
 * so the component can position the image and hit-test clicks.
 */
final class WheelRenderer {

	static final int BORDER = 10;
	private static final int MAXFONTSIZE = 80;
	private static final int MINFONTSIZE = 10;

	Font font;
	List<Color> colors;
	Wheel.Shape shape = Wheel.Shape.CIRCLE;
	boolean hasBorders;

	int radius;
	Point2D center = new Point2D.Double();
	double zoomFactor = 1;

	BufferedImage render(WheelModel model, int w, int h) {
		/*
		 * Calculate all the necessary parameters for the wheel and draw it
		 * section by section.
		 */
		List<String> items = model.getItems();
		int noElem = model.getNumSections();
		double delta = model.getSectionAngleDeg();
		double rotationAngle = model.getRotationAngleDeg();

		int width = w, height = h;
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = (Graphics2D) img.getGraphics();

		// Calculate radius
		radius = Math.min(img.getWidth(), img.getHeight()) / 2 - BORDER;

		double stringDistanceFromEdge = 0.05 * radius;
		int fontSize, stringWidth, maxStringWidth;

		maxStringWidth = (int) (radius - 2 * stringDistanceFromEdge);
		fontSize = calcFontSize(g2d, stringDistanceFromEdge, maxStringWidth, items, noElem, delta);
		g2d.setFont(new Font(font.getFamily(), font.getStyle(), fontSize));

		// Adjust the parameters (for "zoom in") - if the font size is too small
		if (fontSize < MINFONTSIZE) {
			// Release the first graphics context before the image that owns
			// it is replaced; the second one is disposed before returning.
			g2d.dispose();
			zoomFactor = (double) MINFONTSIZE / fontSize;
			width += (int) 2 * ((zoomFactor * radius) - radius);
			height += (int) 2 * ((zoomFactor * radius) - radius);
			radius = (int) (zoomFactor * radius);
			img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			g2d = (Graphics2D) img.getGraphics();
			stringDistanceFromEdge = 0.05 * radius;
			maxStringWidth = (int) (radius - 2 * stringDistanceFromEdge);
			fontSize = calcFontSize(g2d, stringDistanceFromEdge, maxStringWidth, items, noElem, delta);
		}

		// Calculate center point
		center = new Point2D.Double((double) img.getWidth() / 2, (double) img.getHeight() / 2);

		// Set rendering hints
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2d.rotate(Math.toRadians(rotationAngle), center.getX(), center.getY());

		// Draw center point
		if (hasBorders) {
			g2d.setColor(Color.BLACK);
			g2d.fillArc(
					(int) center.getX() - (int) Math.floor(Math.max(0.01 * radius, 1)),
					(int) center.getY() - (int) Math.floor(Math.max(0.01 * radius, 1)),
					(int) Math.floor(Math.max(0.01 * 2 * radius, 2)),
					(int) Math.floor(Math.max(0.01 * 2 * radius, 2)),
					0, 360);
		}

		// Divide circle and draw strings
		FontMetrics fontMetrics;
		int colorCounter = 0;
		for (int i = noElem - 1; i >= 0; i--) {
			// Draw section border
			if (hasBorders) {
				g2d.setColor(Color.BLACK);
				g2d.drawLine((int) center.getX(), (int) center.getY(),
						(int) center.getX() + radius, (int) center.getY());
			}
			// Fill section depending on the chosen shape
			g2d.setColor(colors.get(colorCounter++ % colors.size()));
			if (shape == Wheel.Shape.UMBRELLA)
				fillTriangle(g2d, delta);
			else //if(shape == Shape.CIRCLE)
				fillArc(g2d, delta);
			// Draw string - rotate half delta, then draw then rotate the other half
			// (to have the string in the middle of the section)
			g2d.rotate(Math.toRadians(delta / 2), center.getX(), center.getY());
			g2d.setColor(Color.BLACK);
			fontMetrics = g2d.getFontMetrics();
			stringWidth = fontMetrics.stringWidth(items.get(i));
			g2d.drawString(items.get(i),
					(int) (center.getX() + maxStringWidth - stringWidth + stringDistanceFromEdge),
					(int) (center.getY() + (double) fontMetrics.getHeight() / 2
							- fontMetrics.getMaxDescent()));
			g2d.rotate(Math.toRadians(delta / 2), center.getX(), center.getY());
		}

		// Release the graphics context; the image itself stays usable.
		g2d.dispose();
		return img;
	}

	private int calcFontSize(Graphics g, double stringDistanceFromEdge, int maxStringWidth,
			List<String> items, int noElem, double delta) {
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
			if (items.get(i).length() > tmpString.length())
				tmpString = items.get(i);
		}

		// Binary search for the largest font size where BOTH width and
		// height constraints are satisfied. Replaces the original two
		// while-loops that incremented/decremented by 1 (O(n) per
		// re-rasterization). Also fixes a latent bug where the original
		// "grow" loop could push fontSize past the height constraint
		// without re-checking.
		int lo = 1;
		int hi = MAXFONTSIZE;
		while (lo < hi) {
			int mid = lo + (hi - lo + 1) / 2;
			g.setFont(new Font(font.getFamily(), font.getStyle(), mid));
			FontMetrics fm = g.getFontMetrics();
			Rectangle2D bounds = fm.getStringBounds(tmpString, g);
			
			double outerEdge = maxStringWidth + stringDistanceFromEdge;
			double innerEdge = outerEdge - bounds.getWidth();
			double availableHeight = 0;
			if (innerEdge >= stringDistanceFromEdge) {
				availableHeight = 2 * innerEdge * Math.sin(Math.toRadians(delta / 2));
			}

			if (bounds.getWidth() <= maxStringWidth && bounds.getHeight() <= availableHeight) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		int fontSize = lo;
		g.setFont(new Font(font.getFamily(), font.getStyle(), fontSize));

		return Math.min(fontSize, MAXFONTSIZE);
	}

	private void fillArc(Graphics g2d, double delta) {
		g2d.fillArc((int) center.getX() - radius, (int) center.getY() - radius,
				2 * radius, 2 * radius, 0, (int) -Math.ceil(delta));
		// use ceil because of decimal part (would be left empty)
		if (hasBorders) {
			g2d.setColor(Color.black);
			g2d.drawArc((int) center.getX() - radius, (int) center.getY() - radius,
					2 * radius, 2 * radius, 0, (int) -Math.ceil(delta));
		}
	}

	private void fillTriangle(Graphics2D g2d, double delta) {
		/*
		 * Method that draws section as a triangle (in case Shape=UMBRELLA was chosen)
		 */
		int[] xpoints = new int[3];
		xpoints[0] = (int) center.getX();
		xpoints[1] = (int) center.getX() + radius;
		int dx = (int) (2 * radius * Math.pow(Math.sin(Math.toRadians(delta / 2)), 2));
		xpoints[2] = xpoints[1] - dx;
		int[] ypoints = new int[3];
		ypoints[0] = (int) center.getY();
		ypoints[1] = (int) center.getY();
		int dy = (int) (2 * radius * Math.sin(Math.toRadians(delta / 2)) * Math.cos(Math.toRadians(delta / 2)));
		ypoints[2] = ypoints[1] + dy;
		g2d.fillPolygon(xpoints, ypoints, 3);
		if (hasBorders) {
			g2d.setColor(Color.black);
			g2d.drawLine(xpoints[1], ypoints[1], xpoints[2], ypoints[2]);
		}
	}
}
