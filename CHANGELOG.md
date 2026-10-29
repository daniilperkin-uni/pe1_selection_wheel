# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and
the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-10-29

First tagged release: the refactored library plus the hardening pass from an
independent QA audit (14 reported defects confirmed and fixed, 2 more found
along the way, 135 tests, CI green).

### Added

- `SelectionWheel` and `Wheel` declare a preferred and minimum size (400×400
  fallback, an explicitly set size wins), so standard layout managers and
  `Window.pack()` no longer collapse the component.
- `hasBorders()` query on both `SelectionWheel` and `Wheel`.
- Regression tests for every defect listed under *Fixed*, plus `WheelMouseTest`
  for click-to-stop hit testing.

### Changed

- The borders mutator is now `setHasBorders(boolean)`; the getter-like
  `hasBorders(boolean)` overload is gone.
- Public signatures take and return `List` instead of `ArrayList`, so
  `List.of(...)` works everywhere.
- `WheelMath.dragDeltaDeg` computes polar-angle differences with `atan2`
  instead of slope quotients.
- Spin direction is documented as it behaves: `+1` rotates clockwise on screen
  (the AWT y axis points down), `-1` counter-clockwise.
- `Wheel.setColorScheme` stores a defensive copy, so an immutable palette no
  longer breaks `addColor`.

### Fixed

- Release velocity inverted and clamped to maximum speed when a drag crossed
  the 0°/360° boundary; the displacement is now measured along the shorter arc.
- `paintComponent` threw `IllegalArgumentException` on the EDT when the
  component had no positive size yet (before layout, hidden tab, resize to 0).
- `ConcurrentModificationException` when a listener added or removed a listener
  inside a callback; events are dispatched over a snapshot.
- `WheelModel.tickSpin` and `SpinStep.compute` accepted `NaN` and `±Infinity`
  tick durations and poisoned the model into a permanent NaN spin.
- Click-to-stop hit testing measured from the offscreen image centre instead of
  the wheel's on-screen rotation centre, so clicks missed the visible wheel
  once the renderer zoomed in.
- A UTF-8 byte order mark survived parsing and corrupted the first item.
- A negative elapsed time (clock adjustment between press and release)
  inverted the computed release velocity.
- `paintComponent` rotated the caller's `Graphics` in place, leaking the angle
  into anything else painted with that context.

### Removed

- `Tick.tickHeight` and its accessors on `Tick` and `SelectionWheel`: the value
  never affected rendering or layout.
- `hasBorders(boolean)` (renamed, see *Changed*).

[1.0.0]: https://github.com/daniilperkin-uni/pe1_selection_wheel/releases/tag/v1.0.0
