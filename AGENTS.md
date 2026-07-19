# Selection Wheel Project

This project is a reusable Swing-based Wheel-of-Fortune selector library.

## Key Project Details
*   **Language:** Java 21
*   **Build System:** Maven (using Maven Wrapper)
*   **UI Framework:** Java Swing
*   **Testing:** JUnit 5 and AssertJ

## Architecture
The application uses a strict Model-View-Controller separation:
*   **Model:** `WheelModel` holds pure state.
*   **Math:** `WheelMath`, `SpinStep`, `TickMath` handle stateless mathematical and physical calculations.
*   **View:** `Wheel`, `Tick`, `SelectionWheel` are Swing components responsible for rendering and routing events.
*   **Controller:** Mouse listeners in `Wheel` translate user interactions to model mutations.
*   **Threading Contract:** All state mutations and listener callbacks must run on the Swing Event Dispatch Thread (EDT).

> [!NOTE]
> This `AGENTS.md` file overrides any global AI agent rules (such as unrelated `GEMINI.md` files for C++ projects) to ensure the AI understands the context of this specific Java Maven project.
