package selectionwheel;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;

/**
 * MainWheel is the main class for the SelectionWheel program.
 * It initializes a graphical user interface (GUI) that features
 * a spinning wheel of selectable items.
 *
 * <p>The application is fully event-driven: instead of busy-wait
 * polling, it registers a {@link WheelListener} on the wheel and
 * updates its info labels via callbacks delivered on the EDT.
 *
 * <p>The wheel supports displaying various items and updates its
 * state as it spins.
 */
public class MainWheel {

	private final JLabel selectedItemLabel = new JLabel("(selection)");
	private final JLabel rotationAngleLabel = new JLabel("(angle)");
	private final JLabel spinSpeedLabel = new JLabel("(speed)");

	/**
	 * The main method initializes the GUI components and registers
	 * a WheelListener that updates the info labels and shows a
	 * dialog whenever the wheel comes to rest.
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
		// Set the dimensions for the GUI window
		int windowWidth = 1000, windowHeight = 1000;

		// Initialize the main JFrame and set its default behavior
		JFrame mainWindow = new JFrame("Selection Wheel");
		mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// Initialize the SelectionWheel and configure its properties
		SelectionWheel wheel = new SelectionWheel(items);
		wheel.hasBorders(true);
		wheel.setBounds(10, 10, 700, 700);

		mainWindow.add(wheel);

		// Set up the labels and components for displaying wheel information in the main window
		setupMainWindowComponents(mainWindow);

		// Register an event-driven listener instead of busy-wait polling.
		wheel.addWheelListener(new WheelListener() {
			@Override
			public void selectionChanged(String selectedItem) {
				selectedItemLabel.setText(selectedItem);
			}

			@Override
			public void rotationChanged(double angleDegrees) {
				rotationAngleLabel.setText(Double.toString(angleDegrees));
			}

			@Override
			public void spinSpeedChanged(double speedDegreesPerSecond) {
				spinSpeedLabel.setText(Double.toString(speedDegreesPerSecond));
			}

			@Override
			public void spinStopped() {
				// Update the speed label one last time (it's now 0).
				spinSpeedLabel.setText("0.0");
				JOptionPane.showMessageDialog(mainWindow,
						"Selection: " + wheel.getSelectedItem());
			}
		});

		// Initialize the labels with default values
		selectedItemLabel.setText(wheel.getSelectedItem());
		rotationAngleLabel.setText(Double.toString(wheel.getRotationAngle()));
		spinSpeedLabel.setText("0.0");

		// Configure the mainWindow's layout and visibility
		mainWindow.setSize(windowWidth, windowHeight);
		mainWindow.setLayout(null);
		// Stop any in-flight spin when the window is closed, so no orphan timer lingers.
		mainWindow.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				wheel.spinStop();
			}
		});
		mainWindow.setVisible(true);
	}

	/**
	 * Sets up the main window components for the application.
	 *
	 * This method initializes and positions labels in the main window
	 * to display information such as the current selection, rotation angle,
	 * and spinning speed of the SelectionWheel. The components are added
	 * to the specified JFrame.
	 *
	 * @param mainWindow the JFrame to which the components are added
	 */
	private void setupMainWindowComponents(JFrame mainWindow) {
		JLabel selectionLabel = new JLabel("Selection: ");
		JLabel angleLabel = new JLabel("Angle: ");
		JLabel speedLabel = new JLabel("Speed: ");
		selectionLabel.setBounds(720, 10, 100, 20);
		selectedItemLabel.setBounds(830, 10, 150, 20);
		angleLabel.setBounds(720, 30, 100, 20);
		rotationAngleLabel.setBounds(830, 30, 150, 20);
		speedLabel.setBounds(720, 50, 100, 20);
		spinSpeedLabel.setBounds(830, 50, 150, 20);
		mainWindow.add(selectionLabel);
		mainWindow.add(angleLabel);
		mainWindow.add(speedLabel);
		mainWindow.add(selectedItemLabel);
		mainWindow.add(rotationAngleLabel);
		mainWindow.add(spinSpeedLabel);
	}
}
