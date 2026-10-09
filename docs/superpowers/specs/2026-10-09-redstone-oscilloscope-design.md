# Redstone Oscilloscope (redscope) — Design

Date: 2026-10-09
Target: Minecraft Java 26.3 "Wilderness Bound", Fabric Loader 0.19.5, Loom 1.17, Gradle 9.6.0
Mappings: the game's own unobfuscated names (no Yarn)

## Goal

A single block that samples the redstone signal strength (0–15) it receives every game tick
and shows the history as a waveform in a screen, like a bench oscilloscope. It is a debugging
tool for redstone circuits and the first project of the mod.

## Scope

In the first version:

- One block, `OscilloscopeBlock`, one input channel.
- Waveform screen opened with right-click: grid, live trace, pause, time scale, trigger, cursors.
- Works in multiplayer.

Out of scope for the first version (YAGNI):

- Multiple channels, probe item with wireless display, in-world rendering on the block face.
- Persisting the sample history to disk.
- Exporting data.

## Components

### OscilloscopeBlock

A full block. It reads the signal strength at its own position the same way a redstone lamp does
(`getBestNeighborSignal`). Right-click opens the screen. Mod id is provisionally `redscope`.

### OscilloscopeBlockEntity (server)

- Ticks every game tick on the server and appends the current signal strength (0–15) to a
  ring buffer of 1024 samples, about 51 s of history.
- The ring buffer is not saved to NBT. The block samples continuously from the moment it is
  placed, so history is available whenever the screen is opened.
- Keeps a set of viewers (players with the screen open). With no viewers it sends nothing.

### Networking

- On open, the server sends the viewer a snapshot of the ring buffer together with the
  current sample counter.
- After that it sends one sample per tick (1 byte plus the counter) to each viewer.
- A viewer is removed on screen close, disconnect, block removal, or when the screen handler's
  `stillValid` check fails (too far away).

### OscilloscopeScreen (client only)

The client holds its own copy of the buffer. All display logic runs on it:

- **Grid**: Y axis 0–15, X axis is time.
- **Pause**: freezes the display. Data keeps arriving in the background. Un-pausing resumes.
- **Time scale**: one screen shows 40 / 100 / 400 / 1024 ticks.
- **Trigger**: off, rising edge, or falling edge, with a level 1–15. Searches the buffer for
  the most recent crossing and uses it as the X origin. If none is found it free-runs and
  shows "no trigger".
- **Cursors**: two draggable vertical lines. The readout shows the difference in game ticks,
  redstone ticks (2 game ticks, the unit repeater delay is given in), seconds, and the
  equivalent frequency.

### Pure logic (no Minecraft dependency)

Three small classes that can be unit-tested with plain JUnit:

- `SampleBuffer`: fixed-size ring buffer with a monotonically increasing counter.
- `TriggerSearch`: finds the most recent edge crossing at a given level.
- `CursorMeasure`: converts a tick difference into game ticks, redstone ticks, seconds, Hz.

Block, block entity, screen and networking are thin glue around them.

## Data flow

```
redstone power -> OscilloscopeBlock -> BlockEntity.tick (server) -> SampleBuffer
                                                                      |
                              snapshot on open, 1 sample/tick after   v
                                                           viewer's client SampleBuffer
                                                                      |
                                      TriggerSearch / CursorMeasure -> Screen render
```

## Rejected alternatives

- **Server does all logic**: trigger and pause state would live in the block entity, so one
  player pausing would affect everyone, and every button press would need a packet.
- **Client samples redstone itself**: the client cannot compute the real redstone state of
  repeaters and comparators, and would only have data while the screen is open.

## Error handling and edge cases

- Chunk unloaded: the block entity does not tick, so there are no samples. The buffer is
  discarded with the block entity. A reloaded block starts a fresh history.
- Viewer far away or disconnected: removed from the viewer set; nothing is sent to them.
- If the client sees a gap in the sample counter (for example after a lag spike), it sends a
  small request packet and the server answers with a fresh snapshot.
- Resolution is one game tick. Glitches shorter than one tick are not visible. This will be
  stated in the in-game description.

## Testing

- Unit tests for `SampleBuffer`, `TriggerSearch`, `CursorMeasure` (wrap-around, no edge
  found, level boundaries, zero tick difference).
- Manual acceptance in `runClient`: a lever, a repeater chain and a clock circuit with a known
  period; the cursor readout must match the known period.

## Open items for the implementation plan

- Required JDK for 26.3 is not stated in the Fabric announcement; confirm from the Fabric
  template. The local JDK is 17.
- Fabric API version for 26.3 to be taken from the template generator.
- Screen handler and payload class names to be checked against the 26.3 unobfuscated names,
  since several Fabric APIs were removed or reshaped in 26.3.
