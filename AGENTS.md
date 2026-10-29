# Selection Wheel Project - Agent Directives

> [!IMPORTANT]
> This `AGENTS.md` file defines project-scoped rules that explicitly OVERRIDE any global AI agent rules, such as `GEMINI.md` describing other unrelated projects. You are working in the `Selection Wheel Project`.

## 1. Project Context
*   **Language:** Java 21
*   **Build System:** Maven (using the bundled Maven Wrapper `mvnw` / `mvnw.cmd`). Do not use system Maven.
*   **UI Framework:** Pure Java Swing / AWT. Do not introduce JavaFX or other external UI libraries.
*   **Testing:** JUnit 5 and AssertJ.
*   **Build & test command:** `./mvnw.cmd -B verify` (Windows) / `./mvnw -B verify`. CI runs the same command under `xvfb-run -a` on Temurin 21 — the Swing tests need a display (`java.awt.headless=false`), so headless Linux requires xvfb.
*   **Core Goal:** A reusable, testable, event-driven wheel-of-fortune selector library.

## 2. Architecture & MVC Split
You must respect the strict separation of concerns in this codebase:
*   **Model (`WheelModel`):** Pure state management. Contains items, rotation state, and spin parameters. Absolutely NO Swing/AWT imports.
*   **Math (`WheelMath`, `SpinStep`, `TickMath`):** Stateless mathematical and physical calculations. Must remain side-effect free.
*   **View (`Wheel`, `Tick`, `SelectionWheel`):** Swing `JPanel` subclasses. Responsible for routing events and delegating painting.
*   **Rendering (`WheelRenderer`):** Package-private, Swing-free Graphics2D painter used by `Wheel`; testable headless on a `BufferedImage`. Its output must stay rotation-independent: `Wheel.paintComponent` applies the model's rotation at paint time (baking it into the image as well rotated the wheel twice whenever the cached image was invalidated by a resize).
*   **Controller:** User interactions (mouse listeners in `Wheel`) are translated to model mutations.

## 3. Threading Contract (CRITICAL)
*   All state mutations (e.g., `setRotationAngle`, `setItems`, `spinStartAsync`, `spinStop`) and listener callbacks MUST execute on the **Swing Event Dispatch Thread (EDT)**.
*   `spinStartAsync` and `spinStop` are EDT-aware and marshal to the EDT via `SwingUtilities.invokeLater` if called from another thread. You must maintain this safety.
*   Animations run on a `javax.swing.Timer` (which is EDT-native), not raw `Thread`s.

## 4. Testing Requirements
*   Maintain the existing high test coverage.
*   Use JUnit 5 (`@Test`, `@ParameterizedTest`, etc.) and AssertJ (`assertThat(...)`).
*   Tests are organized by layer: pure math, pure state, and component integration. Follow this pattern when adding new features.
*   Every behavioural fix lands with a regression test that fails on the old code and passes on the new one, in the class that owns the behaviour (not a new ad-hoc file). Test counts and the per-class table live in `README.md`; update them when the suite changes.
*   Keep the `@{argLine}` prefix in the surefire `argLine` (`pom.xml`) — JaCoCo injects its agent through that placeholder; removing it silently disables coverage.
*   `verify` writes the coverage report to `target/site/jacoco` and CI uploads it as an artifact; there is no coverage threshold gate yet.

## 5. Coding Conventions
*   **Java naming convention (MANDATORY):** all fields, locals, and parameters use standard Java `camelCase`. Do **NOT** use the C++/.NET `_underscore` prefix for fields (e.g. write `model`, not `_model`; `spinTimer`, not `_spinTimer`). Constants use `UPPER_SNAKE_CASE` (`MAXFONTSIZE`, `ITEM_LIMIT`).
*   **Item limit constraint:** the wheel supports at most `ContentReader.ITEM_LIMIT = 100` items (assigned from `WheelModel.ITEM_LIMIT`, the single source of truth). Lists that are null, empty, or larger than 100 must raise `IllegalArgumentException`. The bundled resource `itemlist.txt` is the canonical example.
*   **Angle normalization:** rotation angles are normalized to `[-360, 360)` via `WheelMath.normalizeAngleDeg` (Java `%` preserves the dividend's sign, so negative angles are reported as negative). Any Javadoc describing the rotation range must say `[-360, 360)`, not `[0, 360)`.
*   **No `Thread.sleep` in tests:** prefer a `java.util.concurrent.CountDownLatch` awaited with a timeout when synchronizing on an EDT-delivered callback.
*   **Item list resource:** `itemlist.txt` is read from the classpath, so edits under `src/main/resources` only take effect after a rebuild; the runtime alternative is the "Load list..." button.
*   **Public API style:** take and return `List`, never `ArrayList`, in signatures. Boolean state uses `hasX()` / `isX()` for queries and `setX(...)` for mutators; do not name a mutator like a query.
*   **No no-op public setters:** a setter that cannot affect behaviour is removed, not documented as "informational" (`Tick.tickHeight` was exactly that and is gone).

## 6. Regression contracts (do not reintroduce)

Each item below was a real defect found by a QA audit; the named test guards it. Reverting any of them turns the suite red.

*   **Release velocity is measured along the shorter arc.** `WheelMath.computeInitialSpeedDegPerSec` must not subtract the two normalized angles raw — 350° → 10° is a +20° drag, not −340°. A non-positive elapsed time returns 0 (`WheelMathTest`).
*   **`paintComponent` is a no-op for a non-positive size.** `WheelRenderer` allocates a `BufferedImage` of exactly the component size, so painting before layout (or with a negative width from `SelectionWheel.setBounds`) used to throw on the EDT (`WheelPaintTest`).
*   **`paintComponent` must not mutate the caller's `Graphics`.** Paint on `g.create()` and dispose it; rotating in place leaked the angle into everything else drawn with that context (`WheelPaintTest`).
*   **Listener dispatch iterates a snapshot.** A listener may add or remove listeners inside a callback; iterating the backing `ArrayList` threw `ConcurrentModificationException` (`SelectionWheelIntegrationTest`).
*   **Tick durations must be positive and finite.** `WheelModel.tickSpin` and `SpinStep.compute` reject `NaN` and `±Infinity`, which pass a plain `<= 0` test and poison the model into a permanent NaN spin (`WheelModelTest`, `SpinStepTest`).
*   **`SelectionWheel` declares a preferred and minimum size** (400×400 fallback; an explicitly set size wins). With a null layout the inherited metrics are 0×0 and the component collapses under any layout manager (`SelectionWheelIntegrationTest`).
*   **Click hit-testing uses `rotationCenter`,** not the renderer's image-local `center`: the rendered image can be larger than the component because the renderer zooms in when labels get small (`WheelMouseTest`).
*   **Drag deltas come from `atan2`,** not slope quotients: the slope form returned 0 for every step that touched the vertical diameter (`WheelMathTest`).
*   **`ContentReader` strips a leading UTF-8 BOM** — `\uFEFF` survives `String.trim()` and corrupted the first item (`ContentReaderTest`).

## 7. Documentation and audit artifacts

*   `README.md` and this file are the only tracked Markdown documents. Keep both in sync with the code — test counts, API names, the jar file name and the coverage claim are all facts a reader checks first.
*   `QA_REPORT.md` and `QA_FIX_REPORT.md` are audit artifacts and are **untracked by design**. Never `git add` them, and never edit `QA_REPORT.md` (it is the input the fix report answers).
