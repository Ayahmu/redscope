# Red Scope

A Fabric mod for Minecraft Java 26.3 that adds the Redstone Oscilloscope: a block that records
the redstone signal strength (0-15) it receives every game tick and draws it as a waveform.

- Craft it with 7 iron ingots, 1 glass pane and 1 redstone dust (`IGI / IRI / III`).
- Place it next to a redstone source or a repeater output and right-click it.
- Controls: pause, time scale (40 / 100 / 400 / 1024 ticks), trigger (off / rising / falling,
  level 1-15) and two cursors you drag with the mouse. The readout shows the distance between the
  cursors in game ticks, redstone ticks, seconds and Hz.
- The block records continuously, even while nobody is looking; the last ~51 seconds can be
  inspected. History is not saved when the world closes.
- Resolution is one game tick; pulses shorter than that are invisible.

## Build

Needs JDK 25. `./gradlew build` produces `build/libs/redscope-1.0.0.jar`. Unit tests for the
pure logic: `./gradlew test`.

Design: `docs/superpowers/specs/2026-10-09-redstone-oscilloscope-design.md`.
