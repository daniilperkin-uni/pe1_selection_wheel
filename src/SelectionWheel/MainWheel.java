package SelectionWheel;

import javax.swing.*;
import java.util.*;

/**
 * MainWheel is the main class for the SelectionWheel program.
 * It initializes a graphical user interface (GUI) that features
 * a spinning wheel with a list of movies.
 *
 * The wheel supports displaying various movies and updates its
 * state as it spins.
 */
public class MainWheel {

	static JLabel selectedMovieLabel = new JLabel("(selection)");
	static JLabel rotationAngleLabel = new JLabel("(angle)");
	static JLabel spinSpeedLabel = new JLabel("(speed)");

	/**
	 * The main method initializes the GUI components and handles
	 * the spinning wheel interaction. It updates the labels to show
	 * the selected movie, the wheel's current angle, and the spinning
	 * speed.
	 *
	 *  Preconditions: The list of movies must be non-empty, as it is passed to the SelectionWheel.
	 *  Postconditions: The GUI is fully initialized and displayed to the user.</li>
	 *  The program runs without termination.
	 */
	public static void main(String[] args) throws Exception {

		// Set the dimensions for the GUI window
		int windowWidth = 1000, windowHeigth = 1000;

		// Initialize the main JFrame and set its default behavior
		JFrame mainWindow = new JFrame();
		mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);


		/*
		*	Create and populate the homePetList of movies for the selection wheel
		*/
		ArrayList<String> homePetList = ContentReader.importListOfItems();

		/* Initialize the SelectionWheel and configure its properties
		*
		 */
		SelectionWheel wheel = new SelectionWheel(homePetList);
		wheel.hasBorders(true);
		wheel.setBounds(10, 10, 700, 700);

		//adds the wheel to the main window
		mainWindow.add(wheel);

		// Set up the labels and components for displaying wheel information in the main window
		setupMainWindowComponents(mainWindow);

		/* Configure the mainWindow's layout and visibility
		*
		 */
		mainWindow.setSize(windowWidth, windowHeigth);
		mainWindow.setLayout(null);
		mainWindow.setVisible(true);

		/* Initialize the labels with default values
		*
		 */
		updateSelectedString(wheel);
		updateRotationAngle(wheel);
		updateSpinSpeed(wheel);

		//makes the wheel to be not a circle, but umbrellashaped thing
		//wheel.setShape(Wheel.Shape.UMBRELLA);

		/* Main application loop
		*
		 */
		while(true) {
			// Wait for the wheel to start spinning
			while(!wheel.isSpinning())
			{
				updateSelectedString(wheel);
				updateRotationAngle(wheel);

				Thread.sleep(10);

				if(wheel.isSpinning())
					break;
			}
			/* Update labels while the wheel is spinning
			*
			 */
			while(wheel.isSpinning())
			{
				updateSelectedString(wheel);
				updateRotationAngle(wheel);
				updateSpinSpeed(wheel);

				Thread.sleep(10);

			}
			// Update the speed label after the wheel stops
			updateSpinSpeed(wheel);

			// Show the selected movie in a dialog box
			JOptionPane.showMessageDialog(mainWindow, "Selection: " + wheel.getSelectedString());
		}
	}

	/**
	 * Updates the text of the selected movie label.
	 */
	private static void updateSelectedString(SelectionWheel wheel) {
		selectedMovieLabel.setText(wheel.getSelectedString());
	}

	/**
	 * Updates the text of the rotation angle label.
	 */
	private static void updateRotationAngle(SelectionWheel wheel) {
		rotationAngleLabel.setText(Double.toString(wheel.getRotationAngle()));
	}

	/**
	 * Updates the text of the spin speed label.
	 */
	private static void updateSpinSpeed(SelectionWheel wheel) {
		spinSpeedLabel.setText(Double.toString(wheel.getSpinSpeed()));
	}

	/**
	 * Sets up the main window components for the application.
	 *
	 * This method initializes and positions labels in the main window
	 * to display information such as the current selection, rotation angle,
	 * and spinning speed of the SelectionWheel. The components are added
	 * to the specified JFrame.
	 *
	 *
	 * @param mainWindow the JFrame to which the components are added
	 */
	private static void setupMainWindowComponents(JFrame mainWindow) {
		JLabel selectionLabel = new JLabel("Selection: ");
		JLabel angleLabel = new JLabel("Angle: ");
		JLabel speedLabel = new JLabel("Speed: ");
		selectionLabel.setBounds(720, 10, 100, 20);
		selectedMovieLabel.setBounds(830, 10, 150, 20);
		angleLabel.setBounds(720, 30, 100, 20);
		rotationAngleLabel.setBounds(830, 30, 150, 20);
		speedLabel.setBounds(720, 50, 100, 20);
		spinSpeedLabel.setBounds(830, 50, 150, 20);
		mainWindow.add(selectionLabel);
		mainWindow.add(angleLabel);
		mainWindow.add(speedLabel);
		mainWindow.add(selectedMovieLabel);
		mainWindow.add(rotationAngleLabel);
		mainWindow.add(spinSpeedLabel);
	}

}



