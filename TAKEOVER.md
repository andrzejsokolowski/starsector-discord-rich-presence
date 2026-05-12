# Discord Rich Presence — Mod Takeover Notes

## What this mod does
Displays the player's Starsector session as Discord Rich Presence. Shows commander
name, level, and (planned) current star system, fleet size, credits, flagship name
and class. Uses the orbital bombardment art as the large image and the Starsector
icon as the small image.

---

## Project layout

```
starsector-discord-presence/
├── build.gradle                         Gradle fat-jar build
├── mod_info.json                        Starsector mod descriptor
├── data/config/discord_presence_settings.json   Discord app ID (user fills in)
├── jars/
│   └── discord_presence.jar            Built fat jar — this is what the game loads
├── libs/starsector/
│   └── starfarer.api.jar               Compile-only; copy from your Starsector install
├── src/main/java/
│   ├── com/example/discordpresence/
│   │   ├── DiscordPresenceModPlugin.java   BaseModPlugin — lifecycle entry points
│   │   ├── DiscordPresenceManager.java     Singleton; owns the IPC connection
│   │   └── DiscordPresenceScript.java      EveryFrameScript; polls every 5 s
│   └── com/jagrosh/discordipc/entities/pipe/
│       └── WindowsPipe.java            *** CUSTOM OVERRIDE — see Security section
```

---

## Build

```bash
# From project root (Git Bash on Windows)
gradle jar

# Deploy to Starsector
cp jars/discord_presence.jar "/d/Games/StarSector/mods/discord-rich-presence-0.0.1/jars/discord_presence.jar"
```

**Java target:** 1.8 (source + target compatibility).  
**Fat jar strategy:** `configurations.bundled` collects DiscordIPC + slf4j-nop + gson.
Our compiled classes are added to the jar **first** (`from sourceSets.main.output` before
`from configurations.bundled`), so `DuplicatesStrategy.EXCLUDE` keeps our classes when
there is a name collision with a bundled dependency — which is intentional for the
`WindowsPipe` override described below.

---

## Key dependencies

| Artifact | Role |
|---|---|
| `com.github.jagrosh:DiscordIPC:master-SNAPSHOT` | Discord IPC client (JitPack) |
| `org.slf4j:slf4j-api:1.7.36` + `slf4j-nop` | Required by DiscordIPC; nop silences its logs |
| `com.google.code.gson:gson:2.10.1` | Transitive dep of DiscordIPC; declared explicitly |
| `log4j:log4j:1.2.17` | `compileOnly` — already on Starsector's classpath |
| Starsector API jar | `compileOnly` — already on Starsector's classpath |

---

## Security sandbox bypass (critical — do not remove)

Starsector instruments mod bytecode at class-load time to block certain Java API
calls. `java.io.RandomAccessFile` is one of the blocked classes. DiscordIPC's
`WindowsPipe` uses `RandomAccessFile` to connect to Discord's named pipe
(`\\.\pipe\discord-ipc-0`), so without a workaround it throws:

```
SecurityException: File access and reflection are not allowed to scripts.
```

**Fix:** `src/main/java/com/jagrosh/discordipc/entities/pipe/WindowsPipe.java` is our
own class in the `com.jagrosh.discordipc.entities.pipe` package. It shadows
DiscordIPC's class in the fat jar (see fat-jar ordering above) and replaces
`RandomAccessFile` with `java.nio.channels.FileChannel`, which goes through a
different NIO code path that is **not** intercepted by Starsector's sandbox.

**Path normalisation:** DiscordIPC passes `\\?\pipe\discord-ipc-0` (Windows
extended-length prefix). Java NIO's `Paths.get()` rejects `?` as an illegal
character. The constructor normalises `\\?\` → `\\.\` before calling
`Paths.get()`.

If you ever update DiscordIPC to a new version, keep this file in place. The
abstract methods that must be implemented are `write(byte[])`, `read()`, and
`close()` — check with `javap -private` on the new jar if the signature changes.

---

## How presence updates work

1. `DiscordPresenceModPlugin.onApplicationLoad()` — calls `DiscordPresenceManager.connect()` on a daemon thread.
2. `IPCListener.onReady()` — sets `connected = true`, sends "In the Main Menu" presence.
3. `DiscordPresenceModPlugin.onGameLoad()` — registers a `DiscordPresenceScript` transient script.
4. `DiscordPresenceScript.advance()` — throttled to every 5 s; calls `pushPresence()`.
5. `pushPresence()` reads from `Global.getSector()` and calls `DiscordPresenceManager.updatePresence(details, state)`.

---

## Discord Developer Portal setup

- App name: **Starsector** (or whatever)
- Application ID stored in `data/config/discord_presence_settings.json`
- Large image key: `orbitalbombardment` (1024×576 banner)
- Small image key: `s_icon64` (square icon)
- Art assets uploaded under Rich Presence → Art Assets in the portal

---

## Planned next features

The `DiscordPresenceScript.pushPresence()` method is where all new data should be
added. Relevant Starsector API calls:

```java
// Current location (star system name or "Hyperspace")
LocationAPI loc = Global.getSector().getPlayerFleet().getContainingLocation();
String systemName = (loc != null) ? loc.getName() : "Unknown";

// Fleet size (number of ships)
int fleetSize = Global.getSector().getPlayerFleet().getFleetData()
        .getMembersListCopy().size();

// Credits
long credits = (long) Global.getSector().getPlayerFleet()
        .getCargo().getCredits().get();

// Flagship name and hull class
FleetMemberAPI flagship = Global.getSector().getPlayerFleet().getFlagship();
String flagshipName  = flagship.getShipName();          // e.g. "Onslaught"
String flagshipClass = flagship.getHullSpec().getHullName(); // e.g. "Onslaught-class"
```

Discord's presence card has two text lines:
- **details** (bold) — suggested use: commander + flagship
- **state** (smaller) — suggested use: location + fleet stats

Example layout:
```
details: "Cmdr Alex | Onslaught (XIV Battleship)"
state:   "Corvus · 14 ships · 847,230 ¢"
```

---

## Tested environment

- Starsector 0.97a
- Windows 11
- Java 17+ (bundled JRE — see vmparams for full flags)
- Discord desktop app (must be running before Starsector launches)
