package selectionwheel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

/**
 * MainWheel is the main class for the SelectionWheel program.
 * It initializes a graphical user interface (GUI) that features
 * a spinning wheel of selectable items.
 *
 * <p>The application is fully event-driven: instead of busy-wait
 * polling, it registers a {@link WheelListener} on the wheel and
 * updates its info labels via callbacks delivered on the EDT.
 *
 * <p>UX features:
 * <ul>
 *   <li>Resize-resilient layout via {@link BorderLayout} (no hardcoded
 *       pixel coordinates).</li>
 *   <li>Keyboard shortcuts: {@code Space} or {@code Enter} to spin.</li>
 *   <li>Inline result label and "Spin" button (no modal dialog blocking
 *       the flow).</li>
 * </ul>
 */
public class MainWheel {

	private final JLabel selectedItemLabel = new JLabel("(selection)");
	private final JLabel rotationAngleLabel = new JLabel("(angle)");
	private final JLabel spinSpeedLabel = new JLabel("(speed)");
	private final JLabel resultLabel = new JLabel("Spin the wheel!");
	private final JButton spinButton = new JButton("Spin");

	private SelectionWheel wheel;
	private final Random random = new Random();

	/**
	 * The main method initializes the GUI components and registers
	 * a WheelListener that updates the info labels and the inline
	 * result label whenever the wheel comes to rest.
	 *
	 * <p>Preconditions: the bundled itemlist.txt resource must be
	 * present on the classpath and non-empty.
	 * Postconditions: the GUI is fully initialized and displayed
	 * to the user; the program runs until the window is closed.
	 *
	 * @param args unused
	 */
	public static void main(String[] args) {
		ArrayList<String> items;
		try {
			items = ContentReader.importListOfItems();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null,
					"Failed to load item list: " + e.getMessage(),
					"Selection Wheel", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
			return;
		}

		// Build and show the GUI on the EDT.
		SwingUtilities.invokeLater(() -> new MainWheel().launch(items));
	}

	private void launch(ArrayList<String> items) {
		JFrame mainWindow = new JFrame("Selection Wheel");
		mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainWindow.setLayout(new BorderLayout(10, 10));
		mainWindow.getRootPane().setBorder(new EmptyBorder(10, 10, 10, 10));

		// Initialize the SelectionWheel and configure its properties
		wheel = new SelectionWheel(items);
		wheel.hasBorders(true);

		// The wheel goes in the center - BorderLayout will resize it automatically.
		mainWindow.add(wheel, BorderLayout.CENTER);

		// Info panel on the right (replaces hardcoded x/y labels)
		JPanel infoPanel = createInfoPanel();
		mainWindow.add(infoPanel, BorderLayout.EAST);

		// Control bar at the bottom (replaces the modal JOptionPane)
		JPanel controlPanel = createControlPanel();
		mainWindow.add(controlPanel, BorderLayout.SOUTH);

		// Register an event-driven listener instead of busy-wait polling.
		wheel.addWheelListener(new WheelListener() {
			@Override
			public void selectionChanged(String selectedItem) {
				selectedItemLabel.setText(selectedItem);
			}

			@Override
			public void rotationChanged(double angleDegrees) {
				rotationAngleLabel.setText(String.format("%.1f", angleDegrees));
			}

			@Override
			public void spinSpeedChanged(double speedDegreesPerSecond) {
				spinSpeedLabel.setText(String.format("%.1f", speedDegreesPerSecond));
			}

			@Override
			public void spinStarted() {
				spinButton.setEnabled(false);
				resultLabel.setText("Spinning...");
			}

			@Override
			public void spinStopped() {
				spinSpeedLabel.setText("0.0");
				spinButton.setEnabled(true);
				resultLabel.setText("Selection: " + wheel.getSelectedItem());
			}
		});

		// Initialize the labels with default values
		selectedItemLabel.setText(wheel.getSelectedItem());
		rotationAngleLabel.setText(String.format("%.1f", wheel.getRotationAngle()));
		spinSpeedLabel.setText("0.0");

		// Keyboard shortcuts: Space / Enter to spin
		int condition = JComponent.WHEN_IN_FOCUSED_WINDOW;
		InputMap im = mainWindow.getRootPane().getInputMap(condition);
		ActionMap am = mainWindow.getRootPane().getActionMap();
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "spin");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "spin");
		am.put("spin", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				triggerRandomSpin();
			}
		});

		// Stop any in-flight spin when the window is closed.
		mainWindow.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if (wheel != null) wheel.spinStop();
			}
		});

		// Resizable window with a sensible default size.
		mainWindow.setPreferredSize(new Dimension(1000, 800));
		mainWindow.setMinimumSize(new Dimension(600, 500));
		mainWindow.pack();
		mainWindow.setLocationRelativeTo(null);
		mainWindow.setVisible(true);
	}

	/**
	 * Creates the info panel with labels showing selection, angle, and speed.
	 * Uses GridBagLayout for proper vertical stacking and label alignment.
	 */
	private JPanel createInfoPanel() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createTitledBorder("Info"));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;

		JLabel selectionCaption = new JLabel("Selection:");
		JLabel angleCaption = new JLabel("Angle:");
		JLabel speedCaption = new JLabel("Speed:");

		gbc.gridx = 0; gbc.gridy = 0; panel.add(selectionCaption, gbc);
		gbc.gridx = 1; gbc.gridy = 0; panel.add(selectedItemLabel, gbc);
		gbc.gridx = 0; gbc.gridy = 1; panel.add(angleCaption, gbc);
		gbc.gridx = 1; gbc.gridy = 1; panel.add(rotationAngleLabel, gbc);
		gbc.gridx = 0; gbc.gridy = 2; panel.add(speedCaption, gbc);
		gbc.gridx = 1; gbc.gridy = 2; panel.add(spinSpeedLabel, gbc);

		// Push remaining space to the top so labels don't center vertically.
		gbc.weighty = 1.0;
		gbc.gridx = 0; gbc.gridy = 3; panel.add(Box.createGlue(), gbc);

		return panel;
	}

	/**
	 * Creates the control bar with a result label and a Spin button.
	 * Replaces the original blocking JOptionPane.
	 */
	private JPanel createControlPanel() {
		JPanel panel = new JPanel(new BorderLayout(10, 0));
		panel.setBorder(new EmptyBorder(8, 0, 0, 0));
		resultLabel.setFont(resultLabel.getFont().deriveFont(Font.BOLD, 14f));
		panel.add(resultLabel, BorderLayout.CENTER);
		spinButton.setPreferredSize(new Dimension(100, 30));
		spinButton.addActionListener(e -> triggerRandomSpin());
		panel.add(spinButton, BorderLayout.EAST);
		return panel;
	}

	/**
	 * Starts a random spin: random speed in [180, 360] deg/s,
	 * random direction, using the wheel's default deceleration.
	 */
	private void triggerRandomSpin() {
		if (wheel == null || wheel.isSpinning()) return;
		double speed = 180 + random.nextDouble() * 180;
		int direction = random.nextBoolean() ? 1 : -1;
		wheel.spinStartAsync(speed, direction, wheel.getSpinDeceleration());
	}
}
