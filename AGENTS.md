# Selection Wheel Project - Agent Directives

> [!IMPORTANT]
> This `AGENTS.md` file defines project-scoped rules that explicitly OVERRIDE any global AI agent rules, such as `GEMINI.md` describing other unrelated projects. You are working in the `Selection Wheel Project`.

## 1. Project Context
*   **Language:** Java 21
*   **Build System:** Maven (using the bundled Maven Wrapper `mvnw` / `mvnw.cmd`). Do not use system Maven.
*   **UI Framework:** Pure Java Swing / AWT. Do not introduce JavaFX or other external UI libraries.
*   **Testing:** JUnit 5 and AssertJ.
*   **Core Goal:** A reusable, testable, event-driven wheel-of-fortune selector library.

## 2. Architecture & MVC Split
You must respect the strict separation of concerns in this codebase:
*   **Model (`WheelModel`):** Pure state management. Contains items, rotation state, and spin parameters. Absolutely NO Swing/AWT imports.
*   **Math (`WheelMath`, `SpinStep`, `TickMath`):** Stateless mathematical and physical calculations. Must remain side-effect free.
*   **View (`Wheel`, `Tick`, `SelectionWheel`):** Swing `JPanel` subclasses. Responsible for rendering the model and routing events.
*   **Controller:** User interactions (mouse listeners in `Wheel`) are translated to model mutations.

## 3. Threading Contract (CRITICAL)
*   All state mutations (e.g., `setRotationAngle`, `setItems`, `spinStartAsync`, `spinStop`) and listener callbacks MUST execute on the **Swing Event Dispatch Thread (EDT)**.
*   `spinStartAsync` and `spinStop` are EDT-aware and marshal to the EDT via `SwingUtilities.invokeLater` if called from another thread. You must maintain this safety.
*   Animations run on a `javax.swing.Timer` (which is EDT-native), not raw `Thread`s.

## 4. Testing Requirements
*   Maintain the existing high test coverage.
*   Use JUnit 5 (`@Test`, `@ParameterizedTest`, etc.) and AssertJ (`assertThat(...)`).
*   Tests are organized by layer: pure math, pure state, and component integration. Follow this pattern when adding new features.

## 5. Coding Conventions
*   **Java naming convention (MANDATORY):** all fields, locals, and parameters use standard Java `camelCase`. Do **NOT** use the C++/.NET `_underscore` prefix for fields (e.g. write `model`, not `_model`; `spinTimer`, not `_spinTimer`). Constants use `UPPER_SNAKE_CASE` (`MAXFONTSIZE`, `ITEM_LIMIT`).
*   **Item limit constraint:** the wheel supports at most `ContentReader.ITEM_LIMIT = 100` items (this constant mirrors `Wheel.LIMIT` and `WheelModel.ITEM_LIMIT`). Lists that are null, empty, or larger than 100 must raise `IllegalArgumentException`. The bundled resource `itemlist.txt` is the canonical example.
*   **Angle normalization:** rotation angles are normalized to `[-360, 360)` via `WheelMath.normalizeAngleDeg` (Java `%` preserves the dividend's sign, so negative angles are reported as negative). Any Javadoc describing the rotation range must say `[-360, 360)`, not `[0, 360)`.
*   **No `Thread.sleep` in tests:** prefer a `java.util.concurrent.CountDownLatch` awaited with a timeout when synchronizing on an EDT-delivered callback.
