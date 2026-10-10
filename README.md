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

## Status

The pure logic (ring buffer, trigger search, cursor maths) has unit tests and they pass, and the
mod loads on a 26.3 client. The screen, mouse handling and the client/server sample streaming
were written against the decompiled 26.3 sources but have **not yet been checked in a running
game**. Expect rough edges and please open an issue with what you see.

## Play

Needs Minecraft Java Edition 26.3 and Java 25 (the official launcher bundles it).

1. Install Fabric Loader 0.19.5 or newer for 26.3 with the installer from
   <https://fabricmc.net/use/>.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 26.3 (built against
   `0.161.0+26.3`) and `redscope-<version>.jar` from the
   [Releases](../../releases) page.
3. Put both jars into your `mods` folder:
   - Windows: `%appdata%\.minecraft\mods`
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
4. Start the Fabric 26.3 profile, open a Creative world and run
   `/give @p redscope:oscilloscope` (or find it in the Redstone tab).

On a multiplayer server install the mod on the server and on every client.

Quick check that it measures correctly: put the oscilloscope down, stick a stone button on it,
press the button and right-click the oscilloscope straight away. Set Time to 100, drag the two
cursors to the start and end of the pulse. A stone button pulse is 20 game ticks, so the readout
should say `Δ 20 ticks = 10.0 redstone ticks = 1.00 s = 1.00 Hz` (a wooden button gives 30 ticks).

## Develop

Needs JDK 25 and Git. Nothing else: the Gradle wrapper downloads the rest.

```
git clone https://github.com/Ayahmu/redscope.git
cd redscope
./gradlew runClient     # start a dev client with the mod (Windows: gradlew.bat runClient)
./gradlew test          # unit tests for the pure logic
./gradlew build         # jar in build/libs/redscope-<version>.jar
```

The first run downloads Gradle, Minecraft and its assets, which takes a few minutes.

Layout:

- `src/main/java/dev/redscope/scope/` - the block, block entity (server-side sampling) and menu.
- `src/main/java/dev/redscope/scope/logic/` - ring buffer, trigger search, cursor maths. No
  Minecraft imports, so they are plain JUnit tests in `src/test/`.
- `src/main/java/dev/redscope/net/` - the packets that stream samples to open screens.
- `src/client/java/dev/redscope/client/` - the screen and client-side packet receivers.
- `src/main/resources/` - models, textures, lang files, recipe, loot table.

Minecraft 26.3 ships unobfuscated, so there are no Yarn mappings and many names differ from
1.21 tutorials (`Identifier` instead of `ResourceLocation`, `GuiGraphicsExtractor` instead of
`GuiGraphics`, ...). Check against the real 26.3 sources rather than copying old examples.

Design: `docs/superpowers/specs/2026-10-09-redstone-oscilloscope-design.md`, implementation plan:
`docs/superpowers/plans/2026-10-09-redstone-oscilloscope.md`.

## Releasing

Set `version` in `gradle.properties`, commit, then push a matching tag:

```
git tag v1.0.0 && git push origin v1.0.0
```

The `release` workflow builds, runs the tests and publishes `redscope-1.0.0.jar` as a GitHub
Release. Every push also gets a build from the `build` workflow, downloadable from the run's
Artifacts.

## License

CC0-1.0, see `LICENSE`.
