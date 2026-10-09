# Redstone Oscilloscope Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Fabric mod for Minecraft Java 26.3 with one block, the Redstone Oscilloscope, that samples the redstone signal strength (0-15) it receives every game tick and shows the history as a waveform with pause, time scale, trigger and cursors in a screen opened by right-click.

**Architecture:** The block entity samples on the server into a 1024-sample ring buffer and streams one sample per tick to every player who has the screen open (the screen's menu object is the "viewer" handle). The client keeps its own copy of the history and does all display logic (pause, time scale, trigger search, cursor measurement) locally. Buffer, trigger search, cursor maths and the gap/resync state machine are plain Java classes with no Minecraft imports, unit-tested with JUnit; block, block entity, menu, networking and screen are thin glue around them.

**Tech Stack:** Java 25, Minecraft 26.3 with the game's own unobfuscated names, Fabric Loader 0.19.5, Fabric Loom 1.18-SNAPSHOT, Fabric API 0.161.0+26.3, Gradle 9.7.1 (wrapper), JUnit Jupiter 5.13.4.

Design spec: `docs/superpowers/specs/2026-10-09-redstone-oscilloscope-design.md`

## Global Constraints

- Target Minecraft Java 26.3 "Wilderness Bound"; Fabric Loader 0.19.5; the game's own unobfuscated names, no Yarn mappings. (Spec)
- Toolchain versions come from the official Fabric template for 26.3 (branch `26.3` of `FabricMC/fabric-example-mod`): Loom `1.18-SNAPSHOT`, Gradle 9.7.1, Fabric API `0.161.0+26.3`, Java 25 (`options.release = 25`). The spec quoted Loom 1.17 and Gradle 9.6.0 from the announcement post; the template is newer and wins.
- Mod id `redscope`, package root `dev.redscope`. No Mixins. Client-only classes live only in `src/client`.
- Signal strength 0-15, sampled once per game tick on the server; ring buffer of 1024 samples (about 51 s); the history is not saved to disk. (Spec)
- Screen time scales: 40 / 100 / 400 / 1024 ticks. Trigger: off, rising edge or falling edge, level 1-15, edge placed one quarter from the left. Trigger and pause are client-side state. (Spec)
- Cursor readout in game ticks, redstone ticks (2 game ticks), seconds (20 game ticks) and equivalent frequency. (Spec)
- Everything under `dev.redscope.scope.logic` has no `net.minecraft` imports.
- The screen's `isPauseScreen()` must return `false`, otherwise single-player pauses the game and the block stops sampling.
- Resolution is one game tick; shorter glitches are invisible. (Spec)

## How to run things on this machine

All commands assume the working directory `/home/kyu/workspace/redscope` and start with `source dev-env.sh && ...` (created in Task 1), because each shell call starts fresh. `gw` is a function from that file that runs `./gradlew` with the proxy flags.

- This host has no JDK 25 (only 8 and 17) and no `sudo` without a password, so Task 1 installs Temurin 25 under `~/.local/jdks/`.
- Direct connections to GitHub are very slow here; the HTTP proxy on `127.0.0.1:7890` is fast. Maven/Gradle downloads and `git clone` use it.
- This host has no display. Tasks 6-8 end with in-game checks; run those from a desktop session (copy the project there, or install the built jar `build/libs/redscope-1.0.0.jar` into a normal launcher profile with Fabric Loader 0.19.5 and Fabric API 0.161.0+26.3). A headless load check that proves the mod initialises is described in Task 6, Step 6.

## File Structure

| Path | Responsibility |
|---|---|
| `build.gradle`, `gradle.properties`, `settings.gradle` | Loom project, JUnit, Java 25 |
| `dev-env.sh` | Local shell setup (JDK, proxy, `gw`); not committed |
| `tools/make_texture.py` | Generates the block texture PNG (stdlib only) |
| `tools/test_summary.py` | Prints JUnit results per class |
| `src/main/java/dev/redscope/RedScope.java` | Mod entrypoint, `MOD_ID`, `id()` |
| `src/main/java/dev/redscope/registry/ModBlocks.java` | Block and block item registration |
| `src/main/java/dev/redscope/registry/ModBlockEntities.java` | Block entity type registration |
| `src/main/java/dev/redscope/registry/ModMenus.java` | Menu type registration |
| `src/main/java/dev/redscope/registry/ModPayloads.java` | Network payload registration and the resync handler |
| `src/main/java/dev/redscope/registry/ModCreativeTabs.java` | Puts the item in the Redstone tab |
| `src/main/java/dev/redscope/scope/OscilloscopeBlock.java` | The block: ticker and right-click |
| `src/main/java/dev/redscope/scope/OscilloscopeBlockEntity.java` | Per-tick sampling, viewer list |
| `src/main/java/dev/redscope/scope/OscilloscopeMenu.java` | Slotless menu: viewer handle on the server, history holder on the client |
| `src/main/java/dev/redscope/scope/OpenData.java` | Data sent along with "open screen" |
| `src/main/java/dev/redscope/net/*Payload.java` | Three payloads: sample, snapshot, resync request |
| `src/main/java/dev/redscope/scope/logic/*.java` | Pure logic: `SampleBuffer`, `SampleReceiver`, `CursorMeasure`, `TriggerSearch`, `Edge`, `ViewWindow` |
| `src/client/java/dev/redscope/client/RedScopeClient.java` | Screen registration, client payload receivers |
| `src/client/java/dev/redscope/client/OscilloscopeScreen.java` | The waveform screen |
| `src/main/resources/...` | `fabric.mod.json`, blockstate, models, item definition, lang, loot table, recipe, tags, texture |
| `src/test/java/dev/redscope/scope/logic/*Test.java` | JUnit tests for the pure logic |

## What was verified before this plan was written

- Every Java file here was compiled against the real 26.3 sources, and the end state of Tasks 1, 6, 7 and 8 was built with `./gradlew build`; the 31 unit tests pass.
- The finished mod (Task 8 state) was started on a 26.3 client under a virtual display: log line `Red Scope loaded`, mod listed as `redscope 1.0.0`, no registry or resource errors up to texture-atlas creation.
- **Not verified:** how the screen looks and behaves. This host has no display, so rendering, mouse handling, networking between client and server and the manual checks in Tasks 6-9 have not been run. Expect small visual fixes (spacing, colours) during the in-game checks.

API facts that differ from older tutorials, so you do not "fix" them back:

- `net.minecraft.resources.Identifier` replaces `ResourceLocation`; create with `Identifier.fromNamespaceAndPath`.
- Blocks have no `codec()` any more. Register with the public `Blocks.register(ResourceKey<Block>, Function<Properties, Block>, Properties)`; `Properties.setId` is applied for you. Block items are built by hand (`Item.Properties().setId(key).useBlockDescriptionPrefix()`) and `registerBlocks(Item.BY_BLOCK, item)` is called in `ModBlocks.init()`.
- `BlockEntityType` has a public constructor taking `Set<Block>`.
- Menus: `ExtendedMenuType` / `ExtendedMenuProvider` from `fabric-menu-api-v1` (the old screen-handler API is gone). Screens register with `MenuScreens.register`.
- Rendering: `GuiGraphicsExtractor` replaces `GuiGraphics`; screens override `extractRenderState`; mouse callbacks take `MouseButtonEvent`, keys take `KeyEvent`.
- Data paths are singular: `loot_table`, `recipe`, `tags/block`. Item models need `assets/<ns>/items/<id>.json`.

---

### Task 1: Project scaffold and toolchain

**Files:**
- Create: `dev-env.sh`, `.gitignore`, `.gitattributes`, `LICENSE`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*` (from the official template)
- Create: `build.gradle`, `gradle.properties`, `settings.gradle`
- Create: `src/main/resources/fabric.mod.json`, `src/main/resources/assets/redscope/icon.png`
- Create: `src/main/java/dev/redscope/RedScope.java`
- Create: `tools/test_summary.py`

**Interfaces:**
- Produces: `dev.redscope.RedScope.MOD_ID` (`String`), `RedScope.LOGGER` (`org.slf4j.Logger`), `RedScope.id(String)` returning `net.minecraft.resources.Identifier`. Every later task uses `RedScope.id`.

- [ ] **Step 1: Install JDK 25 (skip if `java -version` already shows 25 or newer)**

```bash
mkdir -p ~/.local/jdks/dl-temurin-25 && cd ~/.local/jdks/dl-temurin-25
curl -s -x http://127.0.0.1:7890 -m 30 \
  "https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse" -o meta.json
python3 -I - <<'EOF'
import json
pkg = json.load(open('meta.json'))[0]['binary']['package']
open('expected.sha256', 'w').write(pkg['checksum'] + '  ' + pkg['name'] + '\n')
open('url.txt', 'w').write(pkg['link'])
print(pkg['name'], pkg['size'])
EOF
curl -sL -x http://127.0.0.1:7890 -m 280 -o "$(cut -d' ' -f3 expected.sha256)" "$(cat url.txt)"
sha256sum -c expected.sha256
mkdir -p ~/.local/jdks/temurin-25
tar -xzf OpenJDK25U-jdk_x64_linux_hotspot_*.tar.gz -C ~/.local/jdks/temurin-25 --strip-components=1
~/.local/jdks/temurin-25/bin/java -version
```

Expected: `sha256sum` prints `...tar.gz: OK`; the last command prints `openjdk version "25.0.x"` and `Temurin-25...`.

- [ ] **Step 2: Copy the Gradle wrapper and housekeeping files from the official template**

```bash
mkdir -p ~/.local/jdks/dl-template && cd ~/.local/jdks/dl-template
git -c http.proxy=http://127.0.0.1:7890 clone --depth 1 --branch 26.3 https://github.com/FabricMC/fabric-example-mod.git src
cd /home/kyu/workspace/redscope
T=~/.local/jdks/dl-template/src
cp -r $T/gradlew $T/gradlew.bat $T/gradle $T/LICENSE $T/.gitignore $T/.gitattributes .
mkdir -p src/main/resources/assets/redscope
cp $T/src/main/resources/assets/modid/icon.png src/main/resources/assets/redscope/icon.png
echo 'dev-env.sh' >> .gitignore
grep -c . gradle/wrapper/gradle-wrapper.properties && grep distributionUrl gradle/wrapper/gradle-wrapper.properties
```

Expected: the last line shows `distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip`.

- [ ] **Step 3: Create `dev-env.sh`**

```bash
# Source this file: `source dev-env.sh`. Not committed (listed in .gitignore).
export JAVA_HOME="$HOME/.local/jdks/temurin-25"
export PATH="$JAVA_HOME/bin:$PATH"

# This host reaches Maven and Gradle servers much faster through the local HTTP proxy.
# Delete these two lines on a machine where that is not true.
export GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890"
GRADLE_PROXY_ARGS="$GRADLE_OPTS"

gw() { ./gradlew $GRADLE_PROXY_ARGS "$@"; }
```

- [ ] **Step 4: Create the Gradle project files**

`settings.gradle`:


```groovy
pluginManagement {
	repositories {
		maven {
			name = 'Fabric'
			url = 'https://maven.fabricmc.net/'
		}
		mavenCentral()
		gradlePluginPortal()
	}
}

// Should match your modid
rootProject.name = 'redscope'
```

`gradle.properties`:

```properties
# Done to increase the memory available to gradle.
org.gradle.jvmargs=-Xmx1G
org.gradle.parallel=true
org.gradle.configuration-cache=true

# Fabric Properties
# check these on https://fabricmc.net/develop
minecraft_version=26.3
loader_version=0.19.5
loom_version=1.18-SNAPSHOT

# Mod Properties
version=1.0.0
group=dev.redscope

# Dependencies
fabric_api_version=0.161.0+26.3
```

`build.gradle`:

```groovy
plugins {
	id 'net.fabricmc.fabric-loom' version "${loom_version}"
	id 'maven-publish'
}

repositories {
	// Add repositories to retrieve artifacts from in here.
	// You should only use this when depending on other mods because
	// Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
	// See https://docs.gradle.org/current/userguide/declaring_repositories.html
	// for more information about repositories.
}

loom {
	splitEnvironmentSourceSets()

	mods {
		"redscope" {
			sourceSet sourceSets.main
			sourceSet sourceSets.client
		}
	}
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft "com.mojang:minecraft:${project.minecraft_version}"
	implementation "net.fabricmc:fabric-loader:${project.loader_version}"

	// Fabric API. This is technically optional, but you probably want it anyway.
	implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"

	testImplementation "org.junit.jupiter:junit-jupiter:5.13.4"
	testRuntimeOnly "org.junit.platform:junit-platform-launcher:1.13.4"
}

test {
	useJUnitPlatform()
}

processResources {
	def version = project.version
	inputs.property "version", version

	filesMatching("fabric.mod.json") {
		expand "version": version
	}
}

tasks.withType(JavaCompile).configureEach {
	it.options.release = 25
}

java {
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

jar {
	def projectName = project.name
	inputs.property "projectName", projectName

	from("LICENSE") {
		rename { "${it}_$projectName"}
	}
}

// configure the maven publication
publishing {
	publications {
		create("mavenJava", MavenPublication) {
			from components.java
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}
```

`src/main/resources/fabric.mod.json` (no client entrypoint yet; Task 7 adds it):

```json
{
	"schemaVersion": 1,
	"id": "redscope",
	"version": "${version}",
	"name": "Red Scope",
	"description": "A redstone oscilloscope: watch the signal strength of a circuit as a waveform.",
	"authors": [],
	"license": "CC0-1.0",
	"icon": "assets/redscope/icon.png",
	"environment": "*",
	"entrypoints": {
		"main": [
			"dev.redscope.RedScope"
		]
	},
	"depends": {
		"fabricloader": ">=0.19.5",
		"minecraft": "~26.3",
		"java": ">=25",
		"fabric-api": "*"
	}
}
```

`src/main/java/dev/redscope/RedScope.java` (minimal; Task 6 extends it):

```java
package dev.redscope;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedScope implements ModInitializer {
	public static final String MOD_ID = "redscope";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		LOGGER.info("Red Scope loaded");
	}
}
```

`tools/test_summary.py`:

```python
"""Prints per-class test counts from build/test-results/test/*.xml.

Run from the project root after `gw test`: python3 -I tools/test_summary.py
Exits non-zero if any test failed or errored, or if no results exist.
"""
import glob
import re
import sys

total = 0
bad = 0
files = sorted(glob.glob("build/test-results/test/TEST-*.xml"))
if not files:
    print("no test results found")
    sys.exit(1)
for path in files:
    with open(path, encoding="utf-8") as handle:
        match = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', handle.read())
    tests, skipped, failures, errors = (int(x) for x in match.groups())
    name = path.rsplit("TEST-", 1)[1].removesuffix(".xml").rsplit(".", 1)[1]
    print(f"{name}: {tests} tests, {failures + errors} failed, {skipped} skipped")
    total += tests
    bad += failures + errors
print(f"total: {total} tests, {bad} failed")
sys.exit(1 if bad else 0)
```

- [ ] **Step 5: Build**

Run: `source dev-env.sh && gw build`

Expected: the first run downloads Gradle 9.7.1, Minecraft 26.3 and the libraries (several minutes), then ends with `BUILD SUCCESSFUL`. `ls build/libs` shows `redscope-1.0.0.jar` and `redscope-1.0.0-sources.jar`. If a download stalls, check that the proxy answers: `curl -sI -x http://127.0.0.1:7890 https://maven.fabricmc.net/ | head -1` should print `HTTP/1.1 200`.

- [ ] **Step 6: Commit**

```bash
git add .gitignore .gitattributes LICENSE gradlew gradlew.bat gradle build.gradle gradle.properties settings.gradle src tools
git commit -m "build: scaffold Fabric project for Minecraft 26.3"
```

---

### Task 2: SampleBuffer

**Files:**
- Create: `src/main/java/dev/redscope/scope/logic/SampleBuffer.java`
- Test: `src/test/java/dev/redscope/scope/logic/SampleBufferTest.java`

**Interfaces:**
- Produces (all in `dev.redscope.scope.logic`): `SampleBuffer.CAPACITY` (`int`, 1024); `long count()`; `int size()`; `long oldestIndex()`; `void append(int value)` (0-15 or `IllegalArgumentException`); `int get(long index)` (absolute index within `[oldestIndex(), count())` or `IllegalArgumentException`); `byte[] snapshot()` (retained samples, oldest first); `void restore(long endCount, byte[] samples)` (replaces contents so `count()` becomes `endCount`).

- [ ] **Step 1: Write the failing test**


```java
package dev.redscope.scope.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SampleBufferTest {
	@Test
	void startsEmpty() {
		SampleBuffer buffer = new SampleBuffer();
		assertEquals(0, buffer.count());
		assertEquals(0, buffer.size());
		assertEquals(0, buffer.oldestIndex());
	}

	@Test
	void appendAndGetByAbsoluteIndex() {
		SampleBuffer buffer = new SampleBuffer();
		buffer.append(3);
		buffer.append(15);
		buffer.append(0);
		assertEquals(3, buffer.count());
		assertEquals(3, buffer.size());
		assertEquals(3, buffer.get(0));
		assertEquals(15, buffer.get(1));
		assertEquals(0, buffer.get(2));
	}

	@Test
	void overwritesOldestWhenFull() {
		SampleBuffer buffer = new SampleBuffer();
		for (int i = 0; i < SampleBuffer.CAPACITY + 5; i++) {
			buffer.append(i % 16);
		}
		assertEquals(SampleBuffer.CAPACITY + 5, buffer.count());
		assertEquals(SampleBuffer.CAPACITY, buffer.size());
		assertEquals(5, buffer.oldestIndex());
		assertEquals(5 % 16, buffer.get(5));
		assertEquals((SampleBuffer.CAPACITY + 4) % 16, buffer.get(SampleBuffer.CAPACITY + 4));
	}

	@Test
	void getOutsideRetainedRangeThrows() {
		SampleBuffer buffer = new SampleBuffer();
		for (int i = 0; i < SampleBuffer.CAPACITY + 1; i++) {
			buffer.append(1);
		}
		assertThrows(IllegalArgumentException.class, () -> buffer.get(0));
		assertThrows(IllegalArgumentException.class, () -> buffer.get(buffer.count()));
	}

	@Test
	void appendRejectsOutOfRangeValues() {
		SampleBuffer buffer = new SampleBuffer();
		assertThrows(IllegalArgumentException.class, () -> buffer.append(-1));
		assertThrows(IllegalArgumentException.class, () -> buffer.append(16));
	}

	@Test
	void snapshotRoundTripKeepsAbsoluteIndices() {
		SampleBuffer source = new SampleBuffer();
		for (int i = 0; i < SampleBuffer.CAPACITY + 37; i++) {
			source.append((i * 7) % 16);
		}

		SampleBuffer copy = new SampleBuffer();
		copy.restore(source.count(), source.snapshot());

		assertEquals(source.count(), copy.count());
		assertEquals(source.oldestIndex(), copy.oldestIndex());
		for (long i = source.oldestIndex(); i < source.count(); i++) {
			assertEquals(source.get(i), copy.get(i));
		}
	}

	@Test
	void snapshotOfPartialBufferIsOldestFirst() {
		SampleBuffer buffer = new SampleBuffer();
		buffer.append(1);
		buffer.append(2);
		buffer.append(3);
		assertArrayEquals(new byte[] {1, 2, 3}, buffer.snapshot());
	}

	@Test
	void restoreReplacesPreviousContents() {
		SampleBuffer buffer = new SampleBuffer();
		for (int i = 0; i < 50; i++) {
			buffer.append(9);
		}
		buffer.restore(10, new byte[] {4, 5});
		assertEquals(10, buffer.count());
		assertEquals(2, buffer.size());
		assertEquals(8, buffer.oldestIndex());
		assertEquals(4, buffer.get(8));
		assertEquals(5, buffer.get(9));
	}
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `source dev-env.sh && gw test --tests 'dev.redscope.scope.logic.SampleBufferTest'`

Expected: `BUILD FAILED` with `cannot find symbol` / `class SampleBuffer` in `compileTestJava`.

- [ ] **Step 3: Implement**


```java
package dev.redscope.scope.logic;

/**
 * Fixed-size ring buffer of redstone signal strengths (0-15).
 *
 * <p>Samples are addressed by absolute index: the first sample ever appended has index 0, the next
 * index 1 and so on. Only the newest {@link #CAPACITY} samples are retained.
 */
public final class SampleBuffer {
	public static final int CAPACITY = 1024;

	private final byte[] data = new byte[CAPACITY];
	/** Index of the first sample that exists; non-zero only after {@link #restore}. */
	private long start;
	private long count;

	/** Total number of samples appended so far; also the index the next sample will get. */
	public long count() {
		return this.count;
	}

	/** Number of samples currently retained. */
	public int size() {
		return (int)(this.count - this.oldestIndex());
	}

	/** Absolute index of the oldest retained sample. */
	public long oldestIndex() {
		return Math.max(this.start, this.count - CAPACITY);
	}

	public void append(final int value) {
		if (value < 0 || value > 15) {
			throw new IllegalArgumentException("signal strength out of range: " + value);
		}
		this.data[(int)(this.count % CAPACITY)] = (byte)value;
		this.count++;
	}

	public int get(final long index) {
		if (index < this.oldestIndex() || index >= this.count) {
			throw new IllegalArgumentException("index " + index + " not retained, valid range is [" + this.oldestIndex() + ", " + this.count + ")");
		}
		return this.data[(int)(index % CAPACITY)];
	}

	/** Retained samples, oldest first. */
	public byte[] snapshot() {
		byte[] out = new byte[this.size()];
		long first = this.oldestIndex();
		for (int i = 0; i < out.length; i++) {
			out[i] = this.data[(int)((first + i) % CAPACITY)];
		}
		return out;
	}

	/**
	 * Replaces the contents so that {@code samples} are the newest samples and {@code endCount} is
	 * the total sample count, as produced by {@link #count()} and {@link #snapshot()}.
	 */
	public void restore(final long endCount, final byte[] samples) {
		if (samples.length > CAPACITY || endCount < samples.length) {
			throw new IllegalArgumentException("inconsistent snapshot");
		}
		this.start = endCount - samples.length;
		this.count = this.start;
		for (byte sample : samples) {
			this.append(sample);
		}
	}
}
```

- [ ] **Step 4: Run the tests**

Run: `source dev-env.sh && gw test && python3 -I tools/test_summary.py`

Expected: `BUILD SUCCESSFUL` and `SampleBufferTest: 8 tests, 0 failed, 0 skipped`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/redscope/scope/logic/SampleBuffer.java src/test/java/dev/redscope/scope/logic/SampleBufferTest.java
git commit -m "feat: add SampleBuffer ring buffer with absolute indexing"
```

---

### Task 3: SampleReceiver (gap detection)

**Files:**
- Create: `src/main/java/dev/redscope/scope/logic/SampleReceiver.java`
- Test: `src/test/java/dev/redscope/scope/logic/SampleReceiverTest.java`

**Interfaces:**
- Consumes: `SampleBuffer` from Task 2.
- Produces: `SampleReceiver` with `SampleBuffer buffer()`, `Outcome onSample(long index, int value)` and `void onSnapshot(long count, byte[] samples)`; nested enum `SampleReceiver.Outcome { APPLIED, DROPPED, GAP }`. `GAP` is returned once per gap; the caller then requests a snapshot; later samples return `DROPPED` until `onSnapshot` arrives.

- [ ] **Step 1: Write the failing test**


```java
package dev.redscope.scope.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.redscope.scope.logic.SampleReceiver.Outcome;
import org.junit.jupiter.api.Test;

class SampleReceiverTest {
	@Test
	void appliesConsecutiveSamples() {
		SampleReceiver receiver = new SampleReceiver();
		assertEquals(Outcome.APPLIED, receiver.onSample(0, 3));
		assertEquals(Outcome.APPLIED, receiver.onSample(1, 9));
		assertEquals(2, receiver.buffer().count());
		assertEquals(9, receiver.buffer().get(1));
	}

	@Test
	void dropsSamplesItAlreadyHas() {
		SampleReceiver receiver = new SampleReceiver();
		receiver.onSample(0, 1);
		receiver.onSample(1, 2);
		assertEquals(Outcome.DROPPED, receiver.onSample(1, 7));
		assertEquals(2, receiver.buffer().count());
		assertEquals(2, receiver.buffer().get(1));
	}

	@Test
	void reportsAGapOnceThenDropsUntilSnapshot() {
		SampleReceiver receiver = new SampleReceiver();
		receiver.onSample(0, 1);
		assertEquals(Outcome.GAP, receiver.onSample(5, 4));
		assertEquals(Outcome.DROPPED, receiver.onSample(6, 4));
		assertEquals(Outcome.DROPPED, receiver.onSample(7, 4));
		assertEquals(1, receiver.buffer().count());
	}

	@Test
	void snapshotEndsTheGapAndStreamingResumes() {
		SampleReceiver receiver = new SampleReceiver();
		receiver.onSample(0, 1);
		receiver.onSample(5, 4);
		receiver.onSnapshot(8, new byte[] {1, 2, 3});
		assertEquals(8, receiver.buffer().count());
		assertEquals(3, receiver.buffer().get(7));
		assertEquals(Outcome.APPLIED, receiver.onSample(8, 6));
		assertEquals(6, receiver.buffer().get(8));
	}

	@Test
	void canBeSeededFromAnOpenScreenSnapshot() {
		SampleReceiver receiver = new SampleReceiver();
		receiver.onSnapshot(100, new byte[] {5, 6});
		assertEquals(Outcome.APPLIED, receiver.onSample(100, 7));
		assertEquals(5, receiver.buffer().get(98));
		assertEquals(7, receiver.buffer().get(100));
	}
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `source dev-env.sh && gw test --tests 'dev.redscope.scope.logic.SampleReceiverTest'`

Expected: `BUILD FAILED`, `cannot find symbol` for `SampleReceiver`.

- [ ] **Step 3: Implement**


```java
package dev.redscope.scope.logic;

/**
 * Client-side end of the sample stream. Applies samples in order and notices when some are
 * missing, in which case the caller should ask the server for a snapshot.
 */
public final class SampleReceiver {
	public enum Outcome {
		/** The sample was appended. */
		APPLIED,
		/** The sample was already known, or a resync is under way; nothing changed. */
		DROPPED,
		/** Samples are missing. Reported once; the caller must request a snapshot. */
		GAP
	}

	private final SampleBuffer buffer = new SampleBuffer();
	private boolean resyncPending;

	public SampleBuffer buffer() {
		return this.buffer;
	}

	public Outcome onSample(final long index, final int value) {
		if (this.resyncPending || index < this.buffer.count()) {
			return Outcome.DROPPED;
		}
		if (index > this.buffer.count()) {
			this.resyncPending = true;
			return Outcome.GAP;
		}
		this.buffer.append(value);
		return Outcome.APPLIED;
	}

	public void onSnapshot(final long count, final byte[] samples) {
		this.buffer.restore(count, samples);
		this.resyncPending = false;
	}
}
```

- [ ] **Step 4: Run the tests**

Run: `source dev-env.sh && gw test && python3 -I tools/test_summary.py`

Expected: `BUILD SUCCESSFUL`; `SampleReceiverTest: 5 tests, 0 failed, 0 skipped`; total 13 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/redscope/scope/logic/SampleReceiver.java src/test/java/dev/redscope/scope/logic/SampleReceiverTest.java
git commit -m "feat: add SampleReceiver to detect missing samples"
```

---

### Task 4: CursorMeasure

**Files:**
- Create: `src/main/java/dev/redscope/scope/logic/CursorMeasure.java`
- Test: `src/test/java/dev/redscope/scope/logic/CursorMeasureTest.java`

**Interfaces:**
- Produces: `record CursorMeasure(long ticks)`; `static CursorMeasure between(long a, long b)` (absolute difference); `double redstoneTicks()` (ticks / 2); `double seconds()` (ticks / 20); `OptionalDouble frequencyHz()` (20 / ticks, empty when ticks is 0).

- [ ] **Step 1: Write the failing test**


```java
package dev.redscope.scope.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CursorMeasureTest {
	@Test
	void convertsTicksToUnits() {
		CursorMeasure measure = CursorMeasure.between(100, 120);
		assertEquals(20, measure.ticks());
		assertEquals(10.0, measure.redstoneTicks(), 1e-9);
		assertEquals(1.0, measure.seconds(), 1e-9);
		assertEquals(1.0, measure.frequencyHz().getAsDouble(), 1e-9);
	}

	@Test
	void orderOfCursorsDoesNotMatter() {
		assertEquals(CursorMeasure.between(5, 25), CursorMeasure.between(25, 5));
	}

	@Test
	void zeroDifferenceHasNoFrequency() {
		CursorMeasure measure = CursorMeasure.between(7, 7);
		assertEquals(0, measure.ticks());
		assertEquals(0.0, measure.seconds(), 1e-9);
		assertTrue(measure.frequencyHz().isEmpty());
	}

	@Test
	void oneRedstoneTickIsTwoGameTicks() {
		CursorMeasure measure = CursorMeasure.between(0, 2);
		assertEquals(1.0, measure.redstoneTicks(), 1e-9);
		assertEquals(10.0, measure.frequencyHz().getAsDouble(), 1e-9);
	}
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `source dev-env.sh && gw test --tests 'dev.redscope.scope.logic.CursorMeasureTest'`

Expected: `BUILD FAILED`, `cannot find symbol` for `CursorMeasure`.

- [ ] **Step 3: Implement**


```java
package dev.redscope.scope.logic;

import java.util.OptionalDouble;

/** Distance between two cursors on the waveform, in game ticks, with unit conversions. */
public record CursorMeasure(long ticks) {
	public static final int GAME_TICKS_PER_SECOND = 20;
	public static final int GAME_TICKS_PER_REDSTONE_TICK = 2;

	public static CursorMeasure between(final long a, final long b) {
		return new CursorMeasure(Math.abs(b - a));
	}

	public double redstoneTicks() {
		return (double)this.ticks / GAME_TICKS_PER_REDSTONE_TICK;
	}

	public double seconds() {
		return (double)this.ticks / GAME_TICKS_PER_SECOND;
	}

	/** Frequency of a signal whose period is this distance; empty when the distance is zero. */
	public OptionalDouble frequencyHz() {
		return this.ticks == 0 ? OptionalDouble.empty() : OptionalDouble.of((double)GAME_TICKS_PER_SECOND / this.ticks);
	}
}
```

- [ ] **Step 4: Run the tests**

Run: `source dev-env.sh && gw test && python3 -I tools/test_summary.py`

Expected: `BUILD SUCCESSFUL`; `CursorMeasureTest: 4 tests, 0 failed, 0 skipped`; total 17 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/redscope/scope/logic/CursorMeasure.java src/test/java/dev/redscope/scope/logic/CursorMeasureTest.java
git commit -m "feat: add CursorMeasure unit conversions"
```

---

### Task 5: TriggerSearch, Edge and ViewWindow

**Files:**
- Create: `src/main/java/dev/redscope/scope/logic/Edge.java`, `ViewWindow.java`, `TriggerSearch.java`
- Test: `src/test/java/dev/redscope/scope/logic/TriggerSearchTest.java`

**Interfaces:**
- Consumes: `SampleBuffer` from Task 2.
- Produces: `enum Edge { OFF, RISING, FALLING }`; `record ViewWindow(long start, boolean triggered, long edge)` (`edge` is -1 when not triggered; `start` may be negative or older than retained, renderers skip columns without samples); `TriggerSearch.find(SampleBuffer, Edge, int level, long minIndex, long maxIndex)` returning `OptionalLong`; `TriggerSearch.resolve(SampleBuffer, long newest, int windowTicks, Edge, int level)` returning `ViewWindow`. The edge is placed `windowTicks / 4` columns from the left and a trigger is only used when the whole window around it is available.

- [ ] **Step 1: Write the failing test**


```java
package dev.redscope.scope.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TriggerSearchTest {
	private static SampleBuffer bufferOf(int... values) {
		SampleBuffer buffer = new SampleBuffer();
		for (int value : values) {
			buffer.append(value);
		}
		return buffer;
	}

	@Test
	void findsMostRecentRisingEdge() {
		// indices:           0  1  2  3  4  5  6  7  8
		SampleBuffer buffer = bufferOf(0, 0, 15, 15, 0, 0, 15, 15, 0);
		assertEquals(6, TriggerSearch.find(buffer, Edge.RISING, 8, 0, 8).getAsLong());
	}

	@Test
	void findsMostRecentFallingEdge() {
		SampleBuffer buffer = bufferOf(0, 0, 15, 15, 0, 0, 15, 15, 0);
		assertEquals(8, TriggerSearch.find(buffer, Edge.FALLING, 8, 0, 8).getAsLong());
	}

	@Test
	void upperBoundSkipsLaterEdges() {
		SampleBuffer buffer = bufferOf(0, 0, 15, 15, 0, 0, 15, 15, 0);
		assertEquals(2, TriggerSearch.find(buffer, Edge.RISING, 8, 0, 5).getAsLong());
	}

	@Test
	void lowerBoundSkipsEarlierEdges() {
		SampleBuffer buffer = bufferOf(0, 0, 15, 15, 0, 0, 15, 15, 0);
		assertTrue(TriggerSearch.find(buffer, Edge.RISING, 8, 7, 8).isEmpty());
	}

	@Test
	void levelIsInclusiveOnTheHighSide() {
		SampleBuffer buffer = bufferOf(3, 4, 5, 4);
		// rising through level 5 happens at index 2 (4 -> 5)
		assertEquals(2, TriggerSearch.find(buffer, Edge.RISING, 5, 0, 3).getAsLong());
		// falling below level 5 happens at index 3 (5 -> 4)
		assertEquals(3, TriggerSearch.find(buffer, Edge.FALLING, 5, 0, 3).getAsLong());
	}

	@Test
	void flatSignalHasNoEdge() {
		SampleBuffer buffer = bufferOf(7, 7, 7, 7);
		assertTrue(TriggerSearch.find(buffer, Edge.RISING, 5, 0, 3).isEmpty());
		assertTrue(TriggerSearch.find(buffer, Edge.FALLING, 5, 0, 3).isEmpty());
	}

	@Test
	void offNeverTriggers() {
		SampleBuffer buffer = bufferOf(0, 15, 0, 15);
		assertTrue(TriggerSearch.find(buffer, Edge.OFF, 5, 0, 3).isEmpty());
	}

	@Test
	void boundsAreClampedToRetainedRange() {
		SampleBuffer buffer = new SampleBuffer();
		for (int i = 0; i < SampleBuffer.CAPACITY + 10; i++) {
			buffer.append(i % 2 == 0 ? 0 : 15);
		}
		// index 0 is no longer retained; the search must not throw
		assertTrue(TriggerSearch.find(buffer, Edge.RISING, 8, -50, buffer.count() + 50).isPresent());
	}

	@Test
	void emptyBufferHasNoEdge() {
		assertTrue(TriggerSearch.find(new SampleBuffer(), Edge.RISING, 8, 0, 100).isEmpty());
	}

	@Test
	void freeRunShowsNewestSamplesWhenTriggerIsOff() {
		SampleBuffer buffer = bufferOf(0, 0, 15, 15, 0, 0, 15, 15, 0, 0);
		ViewWindow window = TriggerSearch.resolve(buffer, 9, 6, Edge.OFF, 8);
		assertEquals(4, window.start());
		assertFalse(window.triggered());
	}

	@Test
	void triggeredWindowPlacesEdgeAtOneQuarter() {
		// window of 8 ticks: the edge sits at offset 2 (one quarter) from the left
		SampleBuffer buffer = bufferOf(0, 0, 0, 0, 0, 0, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15);
		ViewWindow window = TriggerSearch.resolve(buffer, 15, 8, Edge.RISING, 8);
		assertTrue(window.triggered());
		assertEquals(6, window.edge());
		assertEquals(4, window.start());
	}

	@Test
	void triggerNeedsEnoughSamplesAfterTheEdge() {
		// the newest rising edge (index 6) has only 3 samples after it, but a window of 8
		// with the edge at offset 2 needs 5, so the search must fall back to the edge at index 2
		SampleBuffer buffer = bufferOf(0, 0, 15, 15, 0, 0, 15, 15, 15, 15);
		ViewWindow window = TriggerSearch.resolve(buffer, 9, 8, Edge.RISING, 8);
		assertTrue(window.triggered());
		assertEquals(2, window.edge());
	}

	@Test
	void noEdgeFallsBackToFreeRunUntriggered() {
		SampleBuffer buffer = bufferOf(5, 5, 5, 5, 5, 5, 5, 5, 5, 5);
		ViewWindow window = TriggerSearch.resolve(buffer, 9, 8, Edge.RISING, 8);
		assertFalse(window.triggered());
		assertEquals(2, window.start());
	}

	@Test
	void newestIsClampedToWhatExists() {
		SampleBuffer buffer = bufferOf(1, 2, 3);
		ViewWindow window = TriggerSearch.resolve(buffer, 99, 8, Edge.OFF, 8);
		assertEquals(-5, window.start());
	}
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `source dev-env.sh && gw test --tests 'dev.redscope.scope.logic.TriggerSearchTest'`

Expected: `BUILD FAILED`, `cannot find symbol` for `TriggerSearch`, `Edge`, `ViewWindow`.

- [ ] **Step 3: Implement**

`Edge.java`:


```java
package dev.redscope.scope.logic;

public enum Edge {
	OFF,
	RISING,
	FALLING
}
```

`ViewWindow.java`:

```java
package dev.redscope.scope.logic;

/**
 * Which samples one screen of the scope shows.
 *
 * @param start     absolute index of the leftmost column; may be negative or older than the
 *                  buffer retains, the renderer skips columns that have no sample
 * @param triggered true if {@code start} was aligned to a trigger edge
 * @param edge      absolute index of the trigger edge, or -1 when not triggered
 */
public record ViewWindow(long start, boolean triggered, long edge) {
}
```

`TriggerSearch.java`:

```java
package dev.redscope.scope.logic;

import java.util.OptionalLong;

public final class TriggerSearch {
	private TriggerSearch() {
	}

	/**
	 * Finds the most recent edge crossing {@code level} with an index in {@code [minIndex, maxIndex]}.
	 *
	 * <p>A rising edge at index i means sample i-1 is below {@code level} and sample i is at or above
	 * it. A falling edge is the opposite. The bounds are clamped to what the buffer retains.
	 * {@link Edge#OFF} never triggers.
	 *
	 * @return the absolute index of sample i, or empty if there is no such edge
	 */
	public static OptionalLong find(final SampleBuffer buffer, final Edge edge, final int level, final long minIndex, final long maxIndex) {
		if (edge == Edge.OFF || buffer.size() < 2) {
			return OptionalLong.empty();
		}
		long low = Math.max(minIndex, buffer.oldestIndex() + 1);
		long high = Math.min(maxIndex, buffer.count() - 1);
		for (long i = high; i >= low; i--) {
			boolean before = buffer.get(i - 1) >= level;
			boolean now = buffer.get(i) >= level;
			if (edge == Edge.RISING && !before && now) {
				return OptionalLong.of(i);
			}
			if (edge == Edge.FALLING && before && !now) {
				return OptionalLong.of(i);
			}
		}
		return OptionalLong.empty();
	}

	/**
	 * Decides which samples one screen of {@code windowTicks} columns shows.
	 *
	 * <p>With a trigger the newest usable edge is placed one quarter from the left. An edge is
	 * usable if the whole window around it is available: enough samples before it are retained and
	 * enough after it have arrived by {@code newest}. Without a usable edge, or with
	 * {@link Edge#OFF}, the window free-runs and ends at {@code newest}.
	 *
	 * @param newest the newest absolute index to show; clamped to what the buffer holds
	 */
	public static ViewWindow resolve(final SampleBuffer buffer, final long newest, final int windowTicks, final Edge edge, final int level) {
		long last = Math.min(newest, buffer.count() - 1);
		int before = windowTicks / 4;
		int after = windowTicks - before - 1;
		long minEdge = buffer.oldestIndex() + before;
		long maxEdge = last - after;
		OptionalLong found = find(buffer, edge, level, minEdge, maxEdge);
		if (found.isPresent()) {
			return new ViewWindow(found.getAsLong() - before, true, found.getAsLong());
		}
		return new ViewWindow(last - windowTicks + 1, false, -1);
	}
}
```

- [ ] **Step 4: Run the tests**

Run: `source dev-env.sh && gw test && python3 -I tools/test_summary.py`

Expected: `BUILD SUCCESSFUL`; `TriggerSearchTest: 14 tests, 0 failed, 0 skipped`; `total: 31 tests, 0 failed`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/redscope/scope/logic src/test/java/dev/redscope/scope/logic/TriggerSearchTest.java
git commit -m "feat: add TriggerSearch and ViewWindow for stable waveform display"
```

---

### Task 6: Block, block entity and registration (no screen yet)

After this task the block exists in the game, can be placed, mined, crafted and samples every tick on the server, but right-clicking does nothing.

**Files:**
- Create: `tools/make_texture.py`, `src/main/resources/assets/redscope/textures/block/oscilloscope.png` (generated)
- Create: `src/main/resources/assets/redscope/blockstates/oscilloscope.json`, `models/block/oscilloscope.json`, `items/oscilloscope.json`, `lang/en_us.json`, `lang/zh_cn.json`
- Create: `src/main/resources/data/redscope/loot_table/blocks/oscilloscope.json`, `data/redscope/recipe/oscilloscope.json`, `data/minecraft/tags/block/mineable/pickaxe.json`, `data/minecraft/tags/block/needs_stone_tool.json`
- Create: `src/main/java/dev/redscope/registry/ModBlocks.java`, `ModBlockEntities.java`, `ModCreativeTabs.java`
- Create: `src/main/java/dev/redscope/scope/OscilloscopeBlock.java`, `OscilloscopeBlockEntity.java`
- Modify (overwrite): `src/main/java/dev/redscope/RedScope.java`

**Interfaces:**
- Consumes: `RedScope.id`, `SampleBuffer` (Task 2).
- Produces: `ModBlocks.OSCILLOSCOPE` (`Block`), `ModBlocks.OSCILLOSCOPE_ITEM` (`BlockItem`), `ModBlocks.OSCILLOSCOPE_ITEM_KEY`; `ModBlockEntities.OSCILLOSCOPE` (`BlockEntityType<OscilloscopeBlockEntity>`); `OscilloscopeBlockEntity.buffer()` returning the server-side `SampleBuffer`; static `OscilloscopeBlockEntity.serverTick(Level, BlockPos, BlockState, OscilloscopeBlockEntity)`. `ModBlocks.init()`, `ModBlockEntities.init()` and `ModCreativeTabs.init()` are called from `RedScope.onInitialize()`.

- [ ] **Step 1: Generate the texture**

`tools/make_texture.py`:


```python
"""Generates src/main/resources/assets/redscope/textures/block/oscilloscope.png (16x16).

Standard library only. Run from the project root: python3 -I tools/make_texture.py
"""
import struct
import zlib

SIZE = 16
FRAME = (0x3A, 0x3F, 0x45, 0xFF)
FRAME_LIGHT = (0x5A, 0x61, 0x69, 0xFF)
SCREEN = (0x0B, 0x1A, 0x10, 0xFF)
GRID = (0x17, 0x30, 0x22, 0xFF)
TRACE = (0x4C, 0xFF, 0x7A, 0xFF)

pixels = [[FRAME] * SIZE for _ in range(SIZE)]

# bevel: lighter top-left edge
for i in range(SIZE):
    pixels[0][i] = FRAME_LIGHT
    pixels[i][0] = FRAME_LIGHT

# screen area
for y in range(2, 14):
    for x in range(2, 14):
        pixels[y][x] = SCREEN
for i in range(2, 14):
    pixels[8][i] = GRID
    pixels[i][8] = GRID

# square wave: high (y=5) for x 3..6, falls at x=7, low (y=10) for x 7..10, rises at x=11, high for x 11..12
for x in range(3, 7):
    pixels[5][x] = TRACE
for y in range(5, 11):
    pixels[y][7] = TRACE
for x in range(7, 11):
    pixels[10][x] = TRACE
for y in range(5, 11):
    pixels[y][11] = TRACE
for x in range(11, 13):
    pixels[5][x] = TRACE


def chunk(kind, data):
    body = kind + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)
png = (
    b"\x89PNG\r\n\x1a\n"
    + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
    + chunk(b"IDAT", zlib.compress(raw, 9))
    + chunk(b"IEND", b"")
)

with open("src/main/resources/assets/redscope/textures/block/oscilloscope.png", "wb") as out:
    out.write(png)
print("wrote oscilloscope.png", len(png), "bytes")
```

Run: `mkdir -p src/main/resources/assets/redscope/textures/block && python3 -I tools/make_texture.py`

Expected: `wrote oscilloscope.png 136 bytes`, and `file src/main/resources/assets/redscope/textures/block/oscilloscope.png` reports `PNG image data, 16 x 16`.

- [ ] **Step 2: Create the resource files**


`src/main/resources/assets/redscope/blockstates/oscilloscope.json`:

```json
{
  "variants": {
    "": {
      "model": "redscope:block/oscilloscope"
    }
  }
}
```

`src/main/resources/assets/redscope/models/block/oscilloscope.json`:

```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "redscope:block/oscilloscope"
  }
}
```

`src/main/resources/assets/redscope/items/oscilloscope.json`:

```json
{
  "model": {
    "type": "minecraft:model",
    "model": "redscope:block/oscilloscope"
  }
}
```

`src/main/resources/data/redscope/loot_table/blocks/oscilloscope.json`:

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "condition": {
        "type": "minecraft:survives_explosion"
      },
      "entries": [
        {
          "type": "minecraft:item",
          "name": "redscope:oscilloscope"
        }
      ],
      "rolls": 1
    }
  ],
  "random_sequence": "redscope:blocks/oscilloscope"
}
```

`src/main/resources/data/redscope/recipe/oscilloscope.json`:

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "redstone",
  "key": {
    "G": "minecraft:glass_pane",
    "I": "minecraft:iron_ingot",
    "R": "minecraft:redstone"
  },
  "pattern": [
    "IGI",
    "IRI",
    "III"
  ],
  "result": {
    "id": "redscope:oscilloscope"
  }
}
```

`src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json`:

```json
{
  "values": [
    "redscope:oscilloscope"
  ]
}
```

`src/main/resources/data/minecraft/tags/block/needs_stone_tool.json`:

```json
{
  "values": [
    "redscope:oscilloscope"
  ]
}
```

`src/main/resources/assets/redscope/lang/en_us.json` (screen strings are added in Task 8):

```json
{
  "block.redscope.oscilloscope": "Redstone Oscilloscope"
}
```

`src/main/resources/assets/redscope/lang/zh_cn.json`:

```json
{
  "block.redscope.oscilloscope": "红石示波器"
}
```

Validate all JSON: `for f in $(find src/main/resources -name '*.json'); do python3 -I -c "import json; json.load(open('$f'))" || echo "BAD $f"; done`

Expected: no output.

- [ ] **Step 3: Create the registration classes**

`src/main/java/dev/redscope/registry/ModBlocks.java`:


```java
package dev.redscope.registry;

import dev.redscope.RedScope;
import dev.redscope.scope.OscilloscopeBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	public static final ResourceKey<Block> OSCILLOSCOPE_KEY = ResourceKey.create(Registries.BLOCK, RedScope.id("oscilloscope"));
	public static final ResourceKey<Item> OSCILLOSCOPE_ITEM_KEY = ResourceKey.create(Registries.ITEM, RedScope.id("oscilloscope"));

	public static final Block OSCILLOSCOPE = Blocks.register(
		OSCILLOSCOPE_KEY,
		OscilloscopeBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)
	);

	public static final BlockItem OSCILLOSCOPE_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		OSCILLOSCOPE_ITEM_KEY,
		new BlockItem(OSCILLOSCOPE, new Item.Properties().setId(OSCILLOSCOPE_ITEM_KEY).useBlockDescriptionPrefix())
	);

	private ModBlocks() {
	}

	public static void init() {
		// Touching this class runs the static initializers above, which do the registration.
		OSCILLOSCOPE_ITEM.registerBlocks(Item.BY_BLOCK, OSCILLOSCOPE_ITEM);
	}
}
```

`src/main/java/dev/redscope/registry/ModBlockEntities.java`:

```java
package dev.redscope.registry;

import dev.redscope.RedScope;
import dev.redscope.scope.OscilloscopeBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<OscilloscopeBlockEntity> OSCILLOSCOPE = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		RedScope.id("oscilloscope"),
		new BlockEntityType<>(OscilloscopeBlockEntity::new, Set.of(ModBlocks.OSCILLOSCOPE))
	);

	private ModBlockEntities() {
	}

	public static void init() {
	}
}
```

`src/main/java/dev/redscope/registry/ModCreativeTabs.java`:

```java
package dev.redscope.registry;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.world.item.CreativeModeTabs;

public final class ModCreativeTabs {
	private ModCreativeTabs() {
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(output -> output.accept(ModBlocks.OSCILLOSCOPE_ITEM));
	}
}
```

- [ ] **Step 4: Create the block and block entity, and overwrite the entrypoint**

`src/main/java/dev/redscope/scope/OscilloscopeBlock.java`:


```java
package dev.redscope.scope;

import dev.redscope.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class OscilloscopeBlock extends BaseEntityBlock {
	public OscilloscopeBlock(final BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new OscilloscopeBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(final Level level, final BlockState state, final BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.OSCILLOSCOPE, OscilloscopeBlockEntity::serverTick);
	}
}
```

`src/main/java/dev/redscope/scope/OscilloscopeBlockEntity.java`:

```java
package dev.redscope.scope;

import dev.redscope.registry.ModBlockEntities;
import dev.redscope.scope.logic.SampleBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Samples the redstone signal at its own position once per game tick. The history is deliberately
 * not saved to disk.
 */
public class OscilloscopeBlockEntity extends BlockEntity {
	private final SampleBuffer buffer = new SampleBuffer();

	public OscilloscopeBlockEntity(final BlockPos pos, final BlockState state) {
		super(ModBlockEntities.OSCILLOSCOPE, pos, state);
	}

	public static void serverTick(final Level level, final BlockPos pos, final BlockState state, final OscilloscopeBlockEntity scope) {
		scope.buffer.append(level.getBestNeighborSignal(pos));
	}

	public SampleBuffer buffer() {
		return this.buffer;
	}
}
```

`src/main/java/dev/redscope/RedScope.java` (replace the whole file):

```java
package dev.redscope;

import dev.redscope.registry.ModBlockEntities;
import dev.redscope.registry.ModBlocks;
import dev.redscope.registry.ModCreativeTabs;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedScope implements ModInitializer {
	public static final String MOD_ID = "redscope";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModBlocks.init();
		ModBlockEntities.init();
		ModCreativeTabs.init();
		LOGGER.info("Red Scope loaded");
	}
}
```

- [ ] **Step 5: Build and run the unit tests**

Run: `source dev-env.sh && gw build && python3 -I tools/test_summary.py`

Expected: `BUILD SUCCESSFUL`; `total: 31 tests, 0 failed`.

- [ ] **Step 6: Check that the game loads the mod**

Headless load check (proves registration, resources and the creative tab load on 26.3; it cannot show a window):

```bash
source dev-env.sh
mkdir -p build
(timeout 400 xvfb-run -a -s "-screen 0 1280x800x24" ./gradlew $GRADLE_PROXY_ARGS runClient --console=plain > build/headless-run.log 2>&1 &)
timeout 300 bash -c 'until grep -qE "Red Scope loaded|BUILD FAILED" build/headless-run.log; do sleep 8; done'
grep -n "Red Scope loaded\|redscope" build/headless-run.log | head
```

Expected: a line `(redscope) Red Scope loaded` and `- redscope 1.0.0` in the mod list. An `ERROR ... Failed to create backend OpenGL ... Couldn't find matching GLX visual` line is expected under Xvfb and harmless. The first run downloads about 500 MB of game assets. Afterwards stop it: `source dev-env.sh && gw --stop`, then `pgrep -af fabric.dli.config` and kill only the process whose command line points at this project's `run` directory.

In-game check, from a desktop session: `source dev-env.sh && gw runClient`, create a Creative world and confirm:

1. The Redstone tab contains "Redstone Oscilloscope" with a dark metal cube and a small green square wave on its faces.
2. It can be placed. In Survival, bare hands drop nothing; a stone or better pickaxe drops the block.
3. Survival crafting table: 7 iron ingots + 1 glass pane + 1 redstone, arranged `IGI / IRI / III`, makes one.
4. Switching the language to 简体中文 shows "红石示波器".
5. `latest.log` has no `redscope` warnings or errors.

- [ ] **Step 7: Commit**

```bash
git add tools/make_texture.py src
git commit -m "feat: add oscilloscope block that samples redstone signal every tick"
```

---

### Task 7: Menu, networking and a read-only screen

After this task right-click opens a screen with a grid and a live trace of the last 100 ticks. Controls come in Task 8.

**Files:**
- Create: `src/main/java/dev/redscope/scope/OpenData.java`, `OscilloscopeMenu.java`
- Create: `src/main/java/dev/redscope/net/SamplePayload.java`, `SnapshotPayload.java`, `ResyncRequestPayload.java`
- Create: `src/main/java/dev/redscope/registry/ModMenus.java`, `ModPayloads.java`
- Create: `src/client/java/dev/redscope/client/RedScopeClient.java`, `OscilloscopeScreen.java`
- Modify (overwrite): `src/main/java/dev/redscope/RedScope.java`, `scope/OscilloscopeBlock.java`, `scope/OscilloscopeBlockEntity.java`, `src/main/resources/fabric.mod.json`

**Interfaces:**
- Consumes: `SampleBuffer`, `SampleReceiver` (+ `Outcome`), `TriggerSearch`, `Edge`, `ViewWindow` from the logic package; `ModBlockEntities.OSCILLOSCOPE`, `RedScope.id`.
- Produces: `OpenData(BlockPos pos, long count, byte[] samples)` with `OpenData.CODEC`; `OscilloscopeMenu` with `pos()`, `buffer()`, `onSample(long, int)` returning `SampleReceiver.Outcome`, `onSnapshot(long, byte[])`, `sendSnapshot()` (server); `ModMenus.OSCILLOSCOPE` (`ExtendedMenuType<OscilloscopeMenu, OpenData>`); payload records `SamplePayload(int containerId, long index, int value)`, `SnapshotPayload(int containerId, long count, byte[] samples)`, `ResyncRequestPayload(int containerId)`, each with `TYPE` and `CODEC`; `OscilloscopeScreen(OscilloscopeMenu, Inventory, Component)` implementing `MenuAccess<OscilloscopeMenu>`.

- [ ] **Step 1: Create the menu data and payloads**

`src/main/java/dev/redscope/scope/OpenData.java`:


```java
package dev.redscope.scope;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What the server sends along with "open this screen": where the scope is and its history so far. */
public record OpenData(BlockPos pos, long count, byte[] samples) {
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenData> CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, OpenData::pos,
		ByteBufCodecs.VAR_LONG, OpenData::count,
		ByteBufCodecs.BYTE_ARRAY, OpenData::samples,
		OpenData::new
	);
}
```

`src/main/java/dev/redscope/net/SamplePayload.java`:

```java
package dev.redscope.net;

import dev.redscope.RedScope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** One new sample, sent to every viewer once per game tick. */
public record SamplePayload(int containerId, long index, int value) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SamplePayload> TYPE = new CustomPacketPayload.Type<>(RedScope.id("sample"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SamplePayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, SamplePayload::containerId,
		ByteBufCodecs.VAR_LONG, SamplePayload::index,
		ByteBufCodecs.VAR_INT, SamplePayload::value,
		SamplePayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
```

`src/main/java/dev/redscope/net/SnapshotPayload.java`:

```java
package dev.redscope.net;

import dev.redscope.RedScope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Full buffer contents, sent in answer to a {@link ResyncRequestPayload}. */
public record SnapshotPayload(int containerId, long count, byte[] samples) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SnapshotPayload> TYPE = new CustomPacketPayload.Type<>(RedScope.id("snapshot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, SnapshotPayload::containerId,
		ByteBufCodecs.VAR_LONG, SnapshotPayload::count,
		ByteBufCodecs.BYTE_ARRAY, SnapshotPayload::samples,
		SnapshotPayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
```

`src/main/java/dev/redscope/net/ResyncRequestPayload.java`:

```java
package dev.redscope.net;

import dev.redscope.RedScope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client noticed a gap in the sample indices and wants a fresh snapshot. */
public record ResyncRequestPayload(int containerId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ResyncRequestPayload> TYPE = new CustomPacketPayload.Type<>(RedScope.id("resync_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ResyncRequestPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, ResyncRequestPayload::containerId,
		ResyncRequestPayload::new
	);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
```

- [ ] **Step 2: Create the menu, registrations and replace the block entity, block and entrypoint**

`src/main/java/dev/redscope/scope/OscilloscopeMenu.java`:


```java
package dev.redscope.scope;

import dev.redscope.net.SamplePayload;
import dev.redscope.net.SnapshotPayload;
import dev.redscope.registry.ModMenus;
import dev.redscope.scope.logic.SampleBuffer;
import dev.redscope.scope.logic.SampleReceiver;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Has no slots. It exists so the server knows who is watching (it is created on open and its
 * {@link #removed} runs on close, disconnect or when {@link #stillValid} fails) and so the client
 * has a place to keep its copy of the history.
 */
public class OscilloscopeMenu extends AbstractContainerMenu {
	private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

	private final BlockPos pos;
	/** Client side only: this viewer's copy of the history. */
	private final SampleReceiver receiver = new SampleReceiver();
	/** Server side only. */
	private final @Nullable OscilloscopeBlockEntity scope;
	private final @Nullable ServerPlayer viewer;

	/** Client side: built from the data the server sent along with the open-screen packet. */
	public OscilloscopeMenu(final int containerId, final Inventory inventory, final OpenData data) {
		super(ModMenus.OSCILLOSCOPE, containerId);
		this.pos = data.pos();
		this.receiver.onSnapshot(data.count(), data.samples());
		this.scope = null;
		this.viewer = null;
	}

	/** Server side. */
	public OscilloscopeMenu(final int containerId, final OscilloscopeBlockEntity scope, final ServerPlayer viewer) {
		super(ModMenus.OSCILLOSCOPE, containerId);
		this.pos = scope.getBlockPos();
		this.scope = scope;
		this.viewer = viewer;
	}

	public BlockPos pos() {
		return this.pos;
	}

	public SampleBuffer buffer() {
		return this.receiver.buffer();
	}

	/** Server side: forward one new sample to this viewer. */
	void sendSample(final long index, final int value) {
		if (this.viewer != null) {
			ServerPlayNetworking.send(this.viewer, new SamplePayload(this.containerId, index, value));
		}
	}

	/** Server side: send this viewer the whole history. */
	public void sendSnapshot() {
		if (this.viewer != null && this.scope != null) {
			SampleBuffer source = this.scope.buffer();
			ServerPlayNetworking.send(this.viewer, new SnapshotPayload(this.containerId, source.count(), source.snapshot()));
		}
	}

	/** Client side: handles a sample that arrived from the server. */
	public SampleReceiver.Outcome onSample(final long index, final int value) {
		return this.receiver.onSample(index, value);
	}

	/** Client side: replaces the history with a snapshot from the server. */
	public void onSnapshot(final long count, final byte[] samples) {
		this.receiver.onSnapshot(count, samples);
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int slotIndex) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(final Player player) {
		if (this.scope == null) {
			return true;
		}
		return !this.scope.isRemoved() && player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) <= MAX_DISTANCE_SQR;
	}

	@Override
	public void removed(final Player player) {
		super.removed(player);
		if (this.scope != null) {
			this.scope.removeViewer(this);
		}
	}
}
```

`src/main/java/dev/redscope/registry/ModMenus.java`:

```java
package dev.redscope.registry;

import dev.redscope.RedScope;
import dev.redscope.scope.OpenData;
import dev.redscope.scope.OscilloscopeMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModMenus {
	public static final ExtendedMenuType<OscilloscopeMenu, OpenData> OSCILLOSCOPE = Registry.register(
		BuiltInRegistries.MENU,
		RedScope.id("oscilloscope"),
		new ExtendedMenuType<>(OscilloscopeMenu::new, OpenData.CODEC)
	);

	private ModMenus() {
	}

	public static void init() {
	}
}
```

`src/main/java/dev/redscope/registry/ModPayloads.java`:

```java
package dev.redscope.registry;

import dev.redscope.net.ResyncRequestPayload;
import dev.redscope.net.SamplePayload;
import dev.redscope.net.SnapshotPayload;
import dev.redscope.scope.OscilloscopeMenu;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ModPayloads {
	private ModPayloads() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(SamplePayload.TYPE, SamplePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SnapshotPayload.TYPE, SnapshotPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ResyncRequestPayload.TYPE, ResyncRequestPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ResyncRequestPayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof OscilloscopeMenu menu && menu.containerId == payload.containerId()) {
				menu.sendSnapshot();
			}
		});
	}
}
```

`src/main/java/dev/redscope/scope/OscilloscopeBlockEntity.java` (replace the whole file; it now streams samples to viewers):

```java
package dev.redscope.scope;

import dev.redscope.registry.ModBlockEntities;
import dev.redscope.scope.logic.SampleBuffer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Samples the redstone signal at its own position once per game tick and streams the samples to
 * every player who has the screen open. The history is deliberately not saved to disk.
 */
public class OscilloscopeBlockEntity extends BlockEntity implements ExtendedMenuProvider<OpenData> {
	private final SampleBuffer buffer = new SampleBuffer();
	private final List<OscilloscopeMenu> viewers = new ArrayList<>();

	public OscilloscopeBlockEntity(final BlockPos pos, final BlockState state) {
		super(ModBlockEntities.OSCILLOSCOPE, pos, state);
	}

	public static void serverTick(final Level level, final BlockPos pos, final BlockState state, final OscilloscopeBlockEntity scope) {
		int signal = level.getBestNeighborSignal(pos);
		scope.buffer.append(signal);
		long index = scope.buffer.count() - 1;
		for (OscilloscopeMenu viewer : scope.viewers) {
			viewer.sendSample(index, signal);
		}
	}

	public SampleBuffer buffer() {
		return this.buffer;
	}

	public void removeViewer(final OscilloscopeMenu menu) {
		this.viewers.remove(menu);
	}

	@Override
	public OpenData getScreenOpeningData(final ServerPlayer player) {
		return new OpenData(this.worldPosition, this.buffer.count(), this.buffer.snapshot());
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.redscope.oscilloscope");
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(final int containerId, final Inventory inventory, final Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return null;
		}
		OscilloscopeMenu menu = new OscilloscopeMenu(containerId, this, serverPlayer);
		this.viewers.add(menu);
		return menu;
	}
}
```

`src/main/java/dev/redscope/scope/OscilloscopeBlock.java` (replace the whole file; adds right-click):

```java
package dev.redscope.scope;

import dev.redscope.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class OscilloscopeBlock extends BaseEntityBlock {
	public OscilloscopeBlock(final BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new OscilloscopeBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(final Level level, final BlockState state, final BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.OSCILLOSCOPE, OscilloscopeBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		MenuProvider provider = state.getMenuProvider(level, pos);
		if (provider != null) {
			player.openMenu(provider);
		}
		return InteractionResult.CONSUME;
	}
}
```

`src/main/java/dev/redscope/RedScope.java` (replace the whole file):

```java
package dev.redscope;

import dev.redscope.registry.ModBlockEntities;
import dev.redscope.registry.ModBlocks;
import dev.redscope.registry.ModCreativeTabs;
import dev.redscope.registry.ModMenus;
import dev.redscope.registry.ModPayloads;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedScope implements ModInitializer {
	public static final String MOD_ID = "redscope";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModBlocks.init();
		ModBlockEntities.init();
		ModMenus.init();
		ModPayloads.init();
		ModCreativeTabs.init();
		LOGGER.info("Red Scope loaded");
	}
}
```

`src/main/resources/fabric.mod.json` (replace the whole file; adds the client entrypoint):

```json
{
	"schemaVersion": 1,
	"id": "redscope",
	"version": "${version}",
	"name": "Red Scope",
	"description": "A redstone oscilloscope: watch the signal strength of a circuit as a waveform.",
	"authors": [],
	"license": "CC0-1.0",
	"icon": "assets/redscope/icon.png",
	"environment": "*",
	"entrypoints": {
		"main": [
			"dev.redscope.RedScope"
		],
		"client": [
			"dev.redscope.client.RedScopeClient"
		]
	},
	"depends": {
		"fabricloader": ">=0.19.5",
		"minecraft": "~26.3",
		"java": ">=25",
		"fabric-api": "*"
	}
}
```

- [ ] **Step 3: Create the client entrypoint and the read-only screen**

`src/client/java/dev/redscope/client/RedScopeClient.java`:


```java
package dev.redscope.client;

import dev.redscope.net.ResyncRequestPayload;
import dev.redscope.net.SamplePayload;
import dev.redscope.net.SnapshotPayload;
import dev.redscope.registry.ModMenus;
import dev.redscope.scope.OscilloscopeMenu;
import dev.redscope.scope.logic.SampleReceiver;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

public class RedScopeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.OSCILLOSCOPE, OscilloscopeScreen::new);

		ClientPlayNetworking.registerGlobalReceiver(SamplePayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof OscilloscopeMenu menu && menu.containerId == payload.containerId()) {
				if (menu.onSample(payload.index(), payload.value()) == SampleReceiver.Outcome.GAP) {
					ClientPlayNetworking.send(new ResyncRequestPayload(menu.containerId));
				}
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(SnapshotPayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof OscilloscopeMenu menu && menu.containerId == payload.containerId()) {
				menu.onSnapshot(payload.count(), payload.samples());
			}
		});
	}
}
```

`src/client/java/dev/redscope/client/OscilloscopeScreen.java`:

```java
package dev.redscope.client;

import dev.redscope.scope.OscilloscopeMenu;
import dev.redscope.scope.logic.Edge;
import dev.redscope.scope.logic.SampleBuffer;
import dev.redscope.scope.logic.TriggerSearch;
import dev.redscope.scope.logic.ViewWindow;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Read-only first version: live trace of the last 100 ticks. Controls are added later. */
public class OscilloscopeScreen extends Screen implements MenuAccess<OscilloscopeMenu> {
	private static final int WINDOW = 100;

	private static final int COLOR_PLOT_BG = 0xFF0B1A10;
	private static final int COLOR_GRID_MINOR = 0xFF173022;
	private static final int COLOR_GRID_MAJOR = 0xFF2B5A3A;
	private static final int COLOR_FRAME = 0xFF8A8A8A;
	private static final int COLOR_TRACE = 0xFF4CFF7A;
	private static final int COLOR_TEXT = 0xFFE0E0E0;

	private final OscilloscopeMenu menu;

	private int plotX;
	private int plotY;
	private int plotW;
	private int plotH;

	public OscilloscopeScreen(final OscilloscopeMenu menu, final Inventory inventory, final Component title) {
		super(title);
		this.menu = menu;
	}

	@Override
	public OscilloscopeMenu getMenu() {
		return this.menu;
	}

	@Override
	protected void init() {
		this.plotW = Math.min(this.width - 60, 400);
		this.plotH = Math.max(64, Math.min(this.height - 110, 176));
		this.plotX = (this.width - this.plotW) / 2;
		this.plotY = 24;
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		SampleBuffer buffer = this.menu.buffer();
		long newest = buffer.count() - 1;
		ViewWindow view = TriggerSearch.resolve(buffer, newest, WINDOW, Edge.OFF, 8);

		graphics.centeredText(this.font, this.title, this.width / 2, 8, COLOR_TEXT);
		this.drawGrid(graphics);
		this.drawTrace(graphics, buffer, view, newest);
		graphics.outline(this.plotX - 1, this.plotY - 1, this.plotW + 2, this.plotH + 2, COLOR_FRAME);
	}

	private int yOf(final int value) {
		return this.plotY + this.plotH - 1 - Math.round(value * (this.plotH - 1) / 15.0F);
	}

	private void drawGrid(final GuiGraphicsExtractor graphics) {
		graphics.fill(this.plotX, this.plotY, this.plotX + this.plotW, this.plotY + this.plotH, COLOR_PLOT_BG);
		for (int value = 0; value <= 15; value++) {
			boolean major = value % 5 == 0;
			graphics.horizontalLine(this.plotX, this.plotX + this.plotW - 1, this.yOf(value), major ? COLOR_GRID_MAJOR : COLOR_GRID_MINOR);
			if (major) {
				graphics.text(this.font, Integer.toString(value), this.plotX - 14, this.yOf(value) - 4, COLOR_TEXT);
			}
		}
		for (int i = 0; i <= 10; i++) {
			int gx = this.plotX + i * (this.plotW - 1) / 10;
			graphics.verticalLine(gx, this.plotY, this.plotY + this.plotH - 1, i % 5 == 0 ? COLOR_GRID_MAJOR : COLOR_GRID_MINOR);
		}
	}

	private void drawTrace(final GuiGraphicsExtractor graphics, final SampleBuffer buffer, final ViewWindow view, final long newest) {
		int previousY = -1;
		for (int px = 0; px < this.plotW; px++) {
			long from = view.start() + (long)px * WINDOW / this.plotW;
			long to = Math.max(from + 1, view.start() + (long)(px + 1) * WINDOW / this.plotW);
			int low = Integer.MAX_VALUE;
			int high = -1;
			int last = -1;
			for (long index = from; index < to; index++) {
				if (index < buffer.oldestIndex() || index >= buffer.count() || index > newest) {
					continue;
				}
				int value = buffer.get(index);
				low = Math.min(low, value);
				high = Math.max(high, value);
				last = value;
			}
			if (last < 0) {
				previousY = -1;
				continue;
			}
			int top = this.yOf(high);
			int bottom = this.yOf(low);
			if (previousY >= 0) {
				top = Math.min(top, previousY);
				bottom = Math.max(bottom, previousY);
			}
			graphics.fill(this.plotX + px, top, this.plotX + px + 1, bottom + 2, COLOR_TRACE);
			previousY = this.yOf(last);
		}
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		}
		if (this.minecraft.options.keyInventory.matches(event)) {
			this.onClose();
			return true;
		}
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.minecraft.player.isAlive() || this.minecraft.player.isRemoved()) {
			this.minecraft.player.closeContainer();
		}
	}

	@Override
	public void onClose() {
		this.minecraft.player.closeContainer();
		super.onClose();
	}

	/** Must stay false: a paused game stops ticking, so the block would stop sampling. */
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}
}
```

- [ ] **Step 4: Build and run the unit tests**

Run: `source dev-env.sh && gw build && python3 -I tools/test_summary.py`

Expected: `BUILD SUCCESSFUL`; `total: 31 tests, 0 failed`. `compileClientJava` must run (it is skipped if `src/client` has no Java files).

- [ ] **Step 5: In-game check (desktop session)**

Run: `source dev-env.sh && gw runClient`. In a Creative world place the oscilloscope and a lever directly against it (or on it). Then:

1. Right-click the oscilloscope. A screen opens with a dark green plot, a grid, labels 0 / 5 / 10 / 15 and the title "Redstone Oscilloscope". The trace is a flat line at 0.
2. Flip the lever on: the trace steps to 15 and keeps scrolling; flip it off: back to 0. The world is not paused (the sun/clouds and mobs keep moving in single player).
3. Close with Esc and with E; both return to the world. Flip the lever on and off a few times with the screen closed, then reopen: the earlier pulses are visible, because the block sampled while nobody was looking.
4. Place a second oscilloscope somewhere else with a different signal; each screen shows only its own block's signal.
5. `latest.log` contains no `redscope` errors and no "Unable to load model" / missing-texture warnings for `redscope:oscilloscope`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "feat: stream samples to a read-only oscilloscope screen"
```

---

### Task 8: Screen controls (pause, time scale, trigger, cursors)

**Files:**
- Modify (overwrite): `src/client/java/dev/redscope/client/OscilloscopeScreen.java`
- Modify (overwrite): `src/main/resources/assets/redscope/lang/en_us.json`, `lang/zh_cn.json`

**Interfaces:**
- Consumes: `CursorMeasure`, `Edge`, `SampleBuffer`, `TriggerSearch`, `ViewWindow`, `OscilloscopeMenu.buffer()`.
- Produces: no new public API; the final screen. Client-only state: `paused`/`frozenNewest`, `window` (40/100/400/1024), `edge`, `level` (1-15), two cursor positions stored as fractions of the plot width so they stay still while the trace scrolls.

- [ ] **Step 1: Replace the screen with the full version**

`src/client/java/dev/redscope/client/OscilloscopeScreen.java` (replace the whole file):


```java
package dev.redscope.client;

import dev.redscope.scope.OscilloscopeMenu;
import dev.redscope.scope.logic.CursorMeasure;
import dev.redscope.scope.logic.Edge;
import dev.redscope.scope.logic.SampleBuffer;
import dev.redscope.scope.logic.TriggerSearch;
import dev.redscope.scope.logic.ViewWindow;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class OscilloscopeScreen extends Screen implements MenuAccess<OscilloscopeMenu> {
	private static final List<Integer> WINDOWS = List.of(40, 100, 400, 1024);
	private static final int MIN_LEVEL = 1;
	private static final int MAX_LEVEL = 15;

	private static final int COLOR_PLOT_BG = 0xFF0B1A10;
	private static final int COLOR_GRID_MINOR = 0xFF173022;
	private static final int COLOR_GRID_MAJOR = 0xFF2B5A3A;
	private static final int COLOR_FRAME = 0xFF8A8A8A;
	private static final int COLOR_TRACE = 0xFF4CFF7A;
	private static final int COLOR_TRIGGER = 0xFFFFD34C;
	private static final int COLOR_CURSOR_A = 0xFF4CC3FF;
	private static final int COLOR_CURSOR_B = 0xFFFF6BD6;
	private static final int COLOR_TEXT = 0xFFE0E0E0;
	private static final int COLOR_WARNING = 0xFFFF8040;

	private final OscilloscopeMenu menu;

	private boolean paused;
	private long frozenNewest;
	private int window = WINDOWS.get(1);
	private Edge edge = Edge.OFF;
	private int level = 8;
	/** Cursor positions as a fraction of the plot width, so they stay put while the trace scrolls. */
	private final double[] cursors = {0.25, 0.5};
	private int dragging = -1;

	private int plotX;
	private int plotY;
	private int plotW;
	private int plotH;
	private Button pauseButton;

	public OscilloscopeScreen(final OscilloscopeMenu menu, final Inventory inventory, final Component title) {
		super(title);
		this.menu = menu;
	}

	@Override
	public OscilloscopeMenu getMenu() {
		return this.menu;
	}

	@Override
	protected void init() {
		this.plotW = Math.min(this.width - 60, 400);
		this.plotH = Math.max(64, Math.min(this.height - 110, 176));
		this.plotX = (this.width - this.plotW) / 2;
		this.plotY = 24;

		int rowY = this.plotY + this.plotH + 6;
		int x = this.plotX;

		this.pauseButton = this.addRenderableWidget(Button.builder(this.pauseLabel(), button -> this.togglePause()).bounds(x, rowY, 56, 20).build());
		x += 60;

		this.addRenderableWidget(
			CycleButton.<Integer>builder(value -> Component.translatable("screen.redscope.window_value", value), this.window)
				.withValues(WINDOWS)
				.create(x, rowY, 86, 20, Component.translatable("screen.redscope.window"), (button, value) -> this.window = value)
		);
		x += 90;

		this.addRenderableWidget(
			CycleButton.<Edge>builder(value -> Component.translatable("screen.redscope.edge." + value.name().toLowerCase(Locale.ROOT)), this.edge)
				.withValues(Edge.values())
				.create(x, rowY, 96, 20, Component.translatable("screen.redscope.trigger"), (button, value) -> this.edge = value)
		);
		x += 100;

		this.addRenderableWidget(Button.builder(Component.literal("-"), button -> this.level = Math.max(MIN_LEVEL, this.level - 1)).bounds(x, rowY, 20, 20).build());
		x += 56;
		this.addRenderableWidget(Button.builder(Component.literal("+"), button -> this.level = Math.min(MAX_LEVEL, this.level + 1)).bounds(x, rowY, 20, 20).build());
	}

	private Component pauseLabel() {
		return Component.translatable(this.paused ? "screen.redscope.run" : "screen.redscope.pause");
	}

	private void togglePause() {
		this.paused = !this.paused;
		if (this.paused) {
			this.frozenNewest = this.menu.buffer().count() - 1;
		}
		this.pauseButton.setMessage(this.pauseLabel());
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		SampleBuffer buffer = this.menu.buffer();
		long newest = this.paused ? this.frozenNewest : buffer.count() - 1;
		ViewWindow view = TriggerSearch.resolve(buffer, newest, this.window, this.edge, this.level);

		graphics.centeredText(this.font, this.title, this.width / 2, 8, COLOR_TEXT);
		this.drawGrid(graphics);
		this.drawTrace(graphics, buffer, view, newest);
		this.drawTriggerLevel(graphics);
		this.drawCursors(graphics);
		graphics.outline(this.plotX - 1, this.plotY - 1, this.plotW + 2, this.plotH + 2, COLOR_FRAME);

		int labelY = this.plotY + this.plotH + 6 + 6;
		graphics.text(this.font, Component.translatable("screen.redscope.level", this.level), this.plotX + 60 + 90 + 100 + 22, labelY, COLOR_TEXT);

		if (this.edge != Edge.OFF && !view.triggered()) {
			graphics.text(this.font, Component.translatable("screen.redscope.no_trigger"), this.plotX + 4, this.plotY + 4, COLOR_WARNING);
		}
		this.drawReadout(graphics);
	}

	private int yOf(final int value) {
		return this.plotY + this.plotH - 1 - Math.round(value * (this.plotH - 1) / 15.0F);
	}

	private void drawGrid(final GuiGraphicsExtractor graphics) {
		graphics.fill(this.plotX, this.plotY, this.plotX + this.plotW, this.plotY + this.plotH, COLOR_PLOT_BG);
		for (int value = 0; value <= 15; value++) {
			boolean major = value % 5 == 0;
			graphics.horizontalLine(this.plotX, this.plotX + this.plotW - 1, this.yOf(value), major ? COLOR_GRID_MAJOR : COLOR_GRID_MINOR);
			if (major) {
				graphics.text(this.font, Integer.toString(value), this.plotX - 14, this.yOf(value) - 4, COLOR_TEXT);
			}
		}
		for (int i = 0; i <= 10; i++) {
			int gx = this.plotX + i * (this.plotW - 1) / 10;
			graphics.verticalLine(gx, this.plotY, this.plotY + this.plotH - 1, i % 5 == 0 ? COLOR_GRID_MAJOR : COLOR_GRID_MINOR);
		}
	}

	private void drawTrace(final GuiGraphicsExtractor graphics, final SampleBuffer buffer, final ViewWindow view, final long newest) {
		int previousY = -1;
		for (int px = 0; px < this.plotW; px++) {
			long from = view.start() + (long)px * this.window / this.plotW;
			long to = Math.max(from + 1, view.start() + (long)(px + 1) * this.window / this.plotW);
			int low = Integer.MAX_VALUE;
			int high = -1;
			int last = -1;
			for (long index = from; index < to; index++) {
				if (index < buffer.oldestIndex() || index >= buffer.count() || index > newest) {
					continue;
				}
				int value = buffer.get(index);
				low = Math.min(low, value);
				high = Math.max(high, value);
				last = value;
			}
			if (last < 0) {
				previousY = -1;
				continue;
			}
			int top = this.yOf(high);
			int bottom = this.yOf(low);
			if (previousY >= 0) {
				top = Math.min(top, previousY);
				bottom = Math.max(bottom, previousY);
			}
			graphics.fill(this.plotX + px, top, this.plotX + px + 1, bottom + 2, COLOR_TRACE);
			previousY = this.yOf(last);
		}
	}

	private void drawTriggerLevel(final GuiGraphicsExtractor graphics) {
		if (this.edge == Edge.OFF) {
			return;
		}
		int ty = this.yOf(this.level);
		for (int dx = 0; dx < this.plotW; dx += 8) {
			graphics.horizontalLine(this.plotX + dx, Math.min(this.plotX + dx + 3, this.plotX + this.plotW - 1), ty, COLOR_TRIGGER);
		}
	}

	private void drawCursors(final GuiGraphicsExtractor graphics) {
		for (int i = 0; i < 2; i++) {
			int cx = this.plotX + (int)Math.round(this.cursors[i] * (this.plotW - 1));
			graphics.verticalLine(cx, this.plotY, this.plotY + this.plotH - 1, i == 0 ? COLOR_CURSOR_A : COLOR_CURSOR_B);
		}
	}

	private long cursorOffset(final int cursor) {
		return Math.round(this.cursors[cursor] * (this.window - 1));
	}

	private void drawReadout(final GuiGraphicsExtractor graphics) {
		CursorMeasure measure = CursorMeasure.between(this.cursorOffset(0), this.cursorOffset(1));
		String frequency = measure.frequencyHz().isPresent() ? String.format(Locale.ROOT, "%.2f", measure.frequencyHz().getAsDouble()) : "--";
		Component text = Component.translatable(
			"screen.redscope.readout",
			measure.ticks(),
			String.format(Locale.ROOT, "%.1f", measure.redstoneTicks()),
			String.format(Locale.ROOT, "%.2f", measure.seconds()),
			frequency
		);
		graphics.text(this.font, text, this.plotX, this.plotY + this.plotH + 6 + 20 + 8, COLOR_TEXT);
	}

	private boolean inPlot(final double x, final double y) {
		return x >= this.plotX && x < this.plotX + this.plotW && y >= this.plotY && y < this.plotY + this.plotH;
	}

	private double fractionAt(final double x) {
		return Math.max(0.0, Math.min(1.0, (x - this.plotX) / (this.plotW - 1)));
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() == 0 && this.inPlot(event.x(), event.y())) {
			double fraction = this.fractionAt(event.x());
			this.dragging = Math.abs(fraction - this.cursors[0]) <= Math.abs(fraction - this.cursors[1]) ? 0 : 1;
			this.cursors[this.dragging] = fraction;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(final MouseButtonEvent event, final double dx, final double dy) {
		if (this.dragging >= 0) {
			this.cursors[this.dragging] = this.fractionAt(event.x());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		this.dragging = -1;
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		}
		if (this.minecraft.options.keyInventory.matches(event)) {
			this.onClose();
			return true;
		}
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.minecraft.player.isAlive() || this.minecraft.player.isRemoved()) {
			this.minecraft.player.closeContainer();
		}
	}

	@Override
	public void onClose() {
		this.minecraft.player.closeContainer();
		super.onClose();
	}

	/** Must stay false: a paused game stops ticking, so the block would stop sampling. */
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}
}
```

- [ ] **Step 2: Replace the language files with the full set**

`src/main/resources/assets/redscope/lang/en_us.json`:

```json
{
  "block.redscope.oscilloscope": "Redstone Oscilloscope",
  "screen.redscope.pause": "Pause",
  "screen.redscope.run": "Run",
  "screen.redscope.window": "Time",
  "screen.redscope.window_value": "%s ticks",
  "screen.redscope.trigger": "Trigger",
  "screen.redscope.edge.off": "Off",
  "screen.redscope.edge.rising": "Rising",
  "screen.redscope.edge.falling": "Falling",
  "screen.redscope.level": "Lv %s",
  "screen.redscope.no_trigger": "NO TRIGGER",
  "screen.redscope.readout": "Δ %s ticks = %s redstone ticks = %s s = %s Hz"
}
```

`src/main/resources/assets/redscope/lang/zh_cn.json`:

```json
{
  "block.redscope.oscilloscope": "红石示波器",
  "screen.redscope.pause": "暂停",
  "screen.redscope.run": "运行",
  "screen.redscope.window": "时基",
  "screen.redscope.window_value": "%s 刻",
  "screen.redscope.trigger": "触发",
  "screen.redscope.edge.off": "关闭",
  "screen.redscope.edge.rising": "上升沿",
  "screen.redscope.edge.falling": "下降沿",
  "screen.redscope.level": "电平 %s",
  "screen.redscope.no_trigger": "未触发",
  "screen.redscope.readout": "Δ %s 游戏刻 = %s 红石刻 = %s 秒 ≈ %s Hz"
}
```

- [ ] **Step 3: Build and run the unit tests**

Run: `source dev-env.sh && gw build && python3 -I tools/test_summary.py`

Expected: `BUILD SUCCESSFUL`; `total: 31 tests, 0 failed`.

- [ ] **Step 4: In-game check (desktop session)**

Run: `source dev-env.sh && gw runClient`. Place the oscilloscope, put a **stone button** on it, open the screen.

The world cannot be touched while a screen is open, so prepare the signal first: press the stone button, right-click the oscilloscope at once, and set the Time button to 400 ticks (20 s of history) so the one-second pulse stays on screen.

1. **Pause**: click "Pause". The label changes to "Run" / "运行" and the pulse stays exactly where it is while time passes. After about 10 seconds click "Run": the trace jumps to the present and scrolls again.
2. **Time scale**: the Time button steps through 40, 100, 400 and 1024 ticks; the pulse gets narrower as the window grows. The cursors stay at the same screen position when the time scale changes, and the readout changes accordingly.
3. **Trigger**: press the button again, open the screen, set Trigger to Rising and the level to 8 (use `+` / `-`, a dashed yellow line shows the level) and the Time to 100 ticks. Until 75 ticks (3.75 s) have passed since the rising edge, "NO TRIGGER" / "未触发" is shown and the trace free-runs, because the window around the edge is not complete yet. After that the trace snaps so that the rising edge is one quarter from the left, and then stays still while time passes. Switch to Falling: the falling edge, 20 ticks later, is the new anchor. With the signal at a constant 0 and Rising selected, "NO TRIGGER" stays.
4. **Level**: `-` and `+` stay within 1 to 15; the dashed line follows.
5. **Cursors**: left-click and drag in the plot. The nearer cursor (blue A or pink B) follows the mouse. The readout under the buttons updates live.

- [ ] **Step 5: Commit**

```bash
git add src
git commit -m "feat: add pause, time scale, trigger and cursor controls"
```

---

### Task 9: Acceptance against known signals, README, final check

**Files:**
- Create: `README.md`

- [ ] **Step 1: Measure a pulse of known length**

A stone button stays pressed for 10 redstone ticks, which is 20 game ticks (1 second); a wooden button for 15 redstone ticks, 30 game ticks. In `gw runClient`, place the oscilloscope, put a stone button on it, press the button, open the screen (the pulse is already in the history), set the time scale to 100 ticks, drag cursor A onto the rising edge and cursor B onto the falling edge.

Expected readout: `Δ 20 ticks = 10.0 redstone ticks = 1.00 s = 1.00 Hz` (Chinese: `Δ 20 游戏刻 = 10.0 红石刻 = 1.00 秒 ≈ 1.00 Hz`). Repeat with a wooden button: `Δ 30 ticks = 15.0 redstone ticks = 1.50 s = 0.67 Hz`. A result within one tick of the expected value is a cursor sitting one column away from the edge; zoom to the 40-tick time scale (10 pixels per tick) and repeat. A result that is off by more than one tick is a bug worth investigating.

- [ ] **Step 2: Measure a clock (optional but recommended)**

Build a redstone torch clock: a torch on a block, with three repeaters (delay 1) in a loop feeding that block. Each stage adds 1 redstone tick, so one half-period is 4 redstone ticks and the period is 8 redstone ticks = 16 game ticks = 0.8 s (1.25 Hz). Put the oscilloscope against a repeater output. With trigger Rising, level 8 and the 40-tick time scale the square wave stands still; put the cursors on two consecutive rising edges and expect `Δ 16 ticks = 8.0 redstone ticks = 0.80 s = 1.25 Hz`. If the loop does not oscillate, check for torch burnout (a loop with too few repeaters) and add a repeater; recompute the expected value as 2 x (torch 1 + sum of repeater delays) redstone ticks.

- [ ] **Step 3: Multiplayer check (optional)**

Open the world to LAN, join with a second client, open the same oscilloscope on both: both show the same signal, and pausing on one does not pause the other. Close one screen; the other keeps updating.

- [ ] **Step 4: Write `README.md`**

```markdown
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
```

- [ ] **Step 5: Final build and tests from a clean state**

Run: `source dev-env.sh && gw clean build && python3 -I tools/test_summary.py && ls build/libs`

Expected: `BUILD SUCCESSFUL`; `total: 31 tests, 0 failed`; `redscope-1.0.0.jar` present.

- [ ] **Step 6: Commit**

```bash
git add README.md
git commit -m "docs: add README"
```

