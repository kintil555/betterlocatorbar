# 🎯 Better Locator Bar

**Minecraft 1.21.11 · Fabric · Client + Server**

Replaces the vanilla locator bar's colored dots with **actual player skin heads**, so you always know *who* is where — not just a random colored dot.

Also includes an **[EXPERIMENTAL]** Player Tracker GUI with real-time coordinates, dimension info, and player search.

---

## ✨ Features

### 🧠 Skin Head Locator Bar
- Replaces every colored dot in the locator bar with the **actual 8×8 skin head** of that player
- Includes hat/overlay layer rendering
- Optional name tag below each head
- Optional border around each head
- Configurable head scale
- Smooth alpha/fade support

### 🗺️ Player Tracker GUI *(Experimental)*
- Open with **`B`** (configurable in Controls settings)
- Shows all players on the server with:
  - Skin head icon
  - Player name
  - XYZ coordinates *(requires server-side mod)*
  - Current dimension (Overworld / Nether / The End / custom)
  - Online indicator
- **Search bar** — filter by player name in real time
- Scrollable list
- Auto-refreshes every 2 seconds

> ⚠️ **Experimental notice:** The GUI coordinate feature requires the mod to be installed server-side too. Without the server component, only the player list (no coordinates) is shown — this is still useful for seeing who's online at a glance.

---

## 📦 Installation

1. Install [Fabric Loader 0.18.1+](https://fabricmc.net/use/)
2. Install [Fabric API 0.139.5+1.21.11](https://modrinth.com/mod/fabric-api)
3. Drop `better-locator-bar-*.jar` into your `mods/` folder
4. Launch Minecraft 1.21.11

### Server-side (optional, for coordinates)
Drop the same jar into your server's `mods/` folder. The mod will automatically handle coordinate sync. No config needed server-side.

---

## ⚙️ Configuration

Config file: `.minecraft/config/betterlocatorbar.json`

| Option | Default | Description |
|--------|---------|-------------|
| `showPlayerHeads` | `true` | Replace dots with skin heads |
| `headScale` | `1.0` | Head icon size multiplier |
| `showNameTag` | `true` | Show player name below head |
| `showHeadBorder` | `true` | Draw border around head |
| `headBorderColor` | `0xFFFFFFFF` | Border color (ARGB) |
| `enableTrackerGui` | `true` | Enable the tracker GUI keybind |
| `showCompassOverlay` | `false` | Show compass direction letters |

---

## 🔨 Building

Requires **Java 21** and **Gradle 9.x**.

```bash
./gradlew build
# Output: build/libs/better-locator-bar-*.jar
```

CI is handled by GitHub Actions (see `.github/workflows/build.yml`).

---

## 🧩 Compatibility

- **Environment:** Client-side (skin heads). Optionally server-side (coordinates).
- **Fabric only** — no Forge/NeoForge support.
- **Minecraft:** 1.21.11 only (uses Loom 1.14.10 + Fabric API 0.139.5)

---

## 📜 License

MIT — free to use, modify, and distribute.
