<div align="center">

# 🎭 Strangers
### *Total Anonymity, Real-Time Proximity Voice Masking & Social Stealth for Minecraft*

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20%20--%201.21+-brightgreen?style=for-the-badge&logo=minecraft)](https://papermc.io)
[![Server Software](https://img.shields.io/badge/Server-Paper%20%7C%20Purpur-blue?style=for-the-badge&logo=papermc)](https://papermc.io)
[![Voice Chat](https://img.shields.io/badge/Voice%20Chat-Simple%20Voice%20Chat%20Hook-orange?style=for-the-badge)](https://modrinth.com/plugin/simple-voice-chat)
[![License](https://img.shields.io/badge/License-Custom-red?style=for-the-badge)](LICENSE)

<br/>

**What if everyone on your server looked identical, sounded unrecognizable, and had only 3 lives?**  
**Strangers** is a turnkey, event-ready social deduction and hardcore SMP plugin designed to turn Minecraft into an intense psychological game of deception, stealth, and survival.

</div>

---

> [!NOTE]
> **Strangers** strips away player identities at both the server logic and network packet layers. Overhead nameplates are removed, skins are unified, tab lists are disguised, proximity voice is shifted in real time, and every death triggers a theatrical cipher reveal. No client-side mods are required for regular gameplay!

---

## 🎬 Custom-Made for Zxynn's Anonymous SMP

This plugin was custom-made for creator **Zxynn** and powered the exact mechanics showcased in **his viral Anonymous SMP video**:

<div align="center">

### [▶ Watch: "How I Became Minecraft's Greatest Anonymous Killer" by Zxynn](https://www.youtube.com/watch?v=6r69C-k_jww)

[![How I Became Minecraft's Greatest Anonymous Killer](https://img.youtube.com/vi/6r69C-k_jww/maxresdefault.jpg)](https://www.youtube.com/watch?v=6r69C-k_jww)

*Experience the exact same anonymity, voice distortion, life counters, tracking compasses, and cinematic death reveals right out of the box!*

</div>

---

## ⚡ Quick Start Guide (Ready in 2 Minutes)

Get your anonymous survival event running in three simple steps:

1. **Install:** Drop `Strangers.jar` into your server's `plugins/` folder.  
   *(Optional but recommended: install **PacketEvents** or **ProtocolLib** for packet cloaking, and **Simple Voice Chat** for proximity voice pitch distortion).*
2. **Configure:** Open `plugins/Strangers/config.yml` to choose your unified disguise skin, starting lives, voice pitch ratio, and world border radius.
3. **Launch Event:** In-game, run `/strangers start` and click **Confirm** in the GUI. The plugin will set the world border, scatter all online players into safe surface terrain under open skies, and activate the anonymity mask!

---

## 🌟 Key Features

### 👤 1. Deep Network-Level Anonymity & Packet Cloaking
* **Packet-Level Disguise:** Intercepts and rewrites outgoing GameProfile, Player Info, and entity metadata packets via **PacketEvents** or **ProtocolLib** to mask player skins and names before data reaches clients.
* **Unified Disguise Skin:** Automatically fetches and caches the designated anonymous skin (e.g. `Velenci` or your custom choice) via Mojang's API into a local SQLite database, avoiding API rate-limiting issues.
* **Overhead Nameplate Removal:** Enforces server-side scoreboard team rules (`NameTagVisibility.NEVER`) so player names never hover over characters in-game.
* **Tab List & Server Ping Cloaking:** Tab list entries display as `Stranger` (or your localized term), and the multiplayer server menu player sample list is completely hidden.
* **100% Vanilla Client Compatible:** Regular players join with a completely vanilla Minecraft client—no required custom mods or resource packs.

---

### 🎙️ 2. Real-Time Proximity Voice Pitch Modulation
* **Simple Voice Chat Integration:** Hooks directly into the **Simple Voice Chat API**.
* **Server-Side Audio DSP:** Processes Opus voice audio packets in real-time, applying an instant pitch shift (default `0.67x` deep disguise) through delay-line crossfading.
* **Unrecognizable Proximity Chat:** Voices become deeply masked and unrecognizable, making it impossible to identify allies or foes simply by listening to them speak.
* **Graceful Degradation:** The plugin operates normally even if Simple Voice Chat is not present on the server.

---

### ❤️ 3. Dynamic Lives & Hardcore Elimination
* **Configurable Lives:** Set starting lives (default: `3`) with customizable PvP vs. PvE deduction rules.
* **Dynamic Actionbar Hearts HUD:** Real-time visual feedback on every player's actionbar:
  * 💛 💛 💛 **3 Lives:** *Golden Hearts (Full Safety)*
  * ❤️ ❤️ **2 Lives:** *Crimson Hearts (Caution)*
  * 🖤 **1 Life:** *Dark Ash Heart (Critical Danger)*
  * ☠ **0 Lives:** *Eliminated (Mask Stripped)*
* **Persistent SQLite Database:** Player lives, original profiles, and revival records persist across server restarts in `database.db`.

---

### 🎭 4. Theatrical Terminal Matrix Death Sequence
* **Silent World:** Vanilla death spam, join messages, and leave notifications are suppressed to maintain complete mystery.
* **Mechanical Cipher Reveal:** When a player loses their final life:
  1. **Intro (1.0s):** An obfuscated matrix cipher (`&k`) flickers across all screens with an ominous Wither ambient rumble.
  2. **Decode (0.5s / letter):** The letters click into place one-by-one with mechanical terminal sounds (`UI_BUTTON_CLICK`).
  3. **Execution Reveal:** The true identity flashes in bold crimson, accompanied by lightning strikes and heavy anvil crashes.
* **Configurable Penalty:** Automatically executes `BAN`, `KICK`, or spectator mode upon full elimination.

---

### 🧭 5. Identity Tracker NameTag (The Hunter's Radar)
* **Craftable Compass Tracker:** Craft an **Identity Tracker** in any crafting table.
* **Player Selection GUI:** Right-clicking opens an interactive head selection menu displaying all online, living players.
* **5-Minute Directional Hunt (Shift + Right-Click to Activate):**
  * **Hunter HUD:** Compass points to target with particle trail indicators and an active BossBar showing remaining time and distance.
  * **Hunted Alert:** The victim receives a pulsing red vignette border and a warning BossBar (*"⚠ YOU ARE BEING HUNTED ⚠"*).
  * **True Identity Reveal:** Unmasks the victim's true name and original skin **strictly for the hunter** for the duration of the track!
* **Offline Protection:** If the target disconnects during the hunt, the timer pauses automatically until they return.

---

### 📖 6. Revive Book (Necromancy Grimoire)
* **High-Tier Craftable Grimoire:** Crafted with Netherite, Enchanted Golden Apples, a Nether Star, and a Book.
* **Resurrection GUI:** Right-click to open a head menu of eliminated players.
* **Strict Balance:** Each player can only be resurrected **once** per event, returning with **1 life**.
* **Global Announcement:** Summons lightning and broadcasts the resurrection to the entire server.

---

### ⚡ 7. One-Click Event Master & Surface Scatter
* **Interactive Confirmation Menus:** Graphical GUIs prevent accidental triggers for `/strangers start` and `/strangers reset`.
* **Automatic World Border:** Sets an 8,000-block border (configurable radius from center).
* **Safe Open-Sky Scatter:** Intelligent algorithm teleports players across separate sectors with guaranteed minimum distance, scanning for solid surface terrain under open skies (avoiding lava, oceans, caves, and tree leaves).
* **Teleport Protection:** Temporary Resistance and invulnerability prevent spawn damage.
* **Instant Event Reset:** Teleports everyone back to spawn, restores lives, pardons eliminated players, and restores the border.

---

### 🛡️ 8. Information Leak & Probing Prevention
* **Command Interception:** Blocks informational commands for non-admin players (`/msg`, `/tell`, `/w`, `/r`, `/whisper`, `/near`, `/who`, `/online`, `/list`, `/team`).
* **Selector Protection:** Disables target selectors (`@p`, `@a`, `@r`, `@e`) so players cannot probe who is connected.
* **Tab-Completion Filtering:** Real player names never appear in command tab completions.
* **Chat Sanitizer:** Automatically masks real names mentioned in chat to `Stranger`.
* **Book Author Cloaking:** Overwrites signed book author metadata to prevent accidental leaks.

---

### 👁️ 9. Admin Bypass & Interactive Dashboard
* **Admin Bypass Mode (`/strangers bypass`):** Staff can toggle bypass mode to see all real names, skins, and unmasked profiles while keeping all players anonymous.
* **Clickable Control Panel (`/strangers status`):** In-game chat dashboard displaying event status, world border, voice hook state, remaining lives, and quick clickable action buttons.

---

## 📜 Crafting Recipes

### 🧭 1. Identity Tracker NameTag
*Bind to any online player to track their coordinates and reveal their identity.*

| Recipe Grid | Materials |
|:---:|:---|
| `[ Eye of Ender ]` `[ Compass ]` `[ Eye of Ender ]`<br/>`[ Compass ]` `[ Name Tag ]` `[ Compass ]`<br/>`[ Eye of Ender ]` `[ Compass ]` `[ Eye of Ender ]` | • **1x** Name Tag<br/>• **4x** Eye of Ender<br/>• **4x** Compass |

---

### 📖 2. Revive Book
*Right-click to open the Resurrection GUI and bring an eliminated player back with 1 life.*

| Recipe Grid | Materials |
|:---:|:---|
| `[ Netherite ]` `[ Netherite ]` `[ Netherite ]`<br/>`[ God Apple ]` `[ Nether Star ]` `[ God Apple ]`<br/>`[ Netherite ]` `[ Book ]` `[ Netherite ]` | • **5x** Netherite Ingot<br/>• **2x** Enchanted Golden Apple<br/>• **1x** Nether Star<br/>• **1x** Book |

*(All recipes, shapes, and ingredients are fully customizable in `config.yml`)*

---

## ⌨️ Commands & Permissions

| Command | Description | Permission | Recommended |
|:---|:---|:---|:---|
| `/strangers status` | Opens the interactive admin dashboard & system status | `strangers.admin` | Admins / Hosts |
| `/strangers start [confirm]` | Opens confirmation GUI to initiate event, set border & scatter players | `strangers.admin` | Admins / Hosts |
| `/strangers reset [confirm]` | Opens confirmation GUI to reset border, restore lives & teleport to spawn | `strangers.admin` | Admins / Hosts |
| `/strangers on` | Enables anonymity mode (masks skins, names & audio) | `strangers.admin` | Admins / Hosts |
| `/strangers off` | Disables anonymity mode (restores original appearances) | `strangers.admin` | Admins / Hosts |
| `/strangers reload` | Reloads configuration, messages, and skin cache | `strangers.admin` | Admins / Hosts |
| `/strangers bypass [on\|off]` | Toggles admin view to see real names and original skins | `strangers.bypass` | Staff / Admins |
| `/strangers lives <get\|set\|add\|remove> <player> [amt]` | Manages lives for a specific player | `strangers.admin` | Admins / Hosts |
| `/strangers revive <player>` | Manually resurrects an eliminated player | `strangers.admin` | Admins / Hosts |
| `/strangers giverevivebook [player] [amount]` | Gives custom Revive Book item(s) to a player | `strangers.admin` | Admins / Hosts |
| `/strangers givetracker [player] [amount]` | Gives custom Identity Tracker NameTag(s) to a player | `strangers.admin` | Admins / Hosts |
| `/strangers deathmessage [on\|off]` | Toggles broadcast announcements when a life is lost | `strangers.admin` | Admins / Hosts |
| `/strangers deathsound [on\|off]` | Toggles global Warden heartbeat / death sound effects | `strangers.admin` | Admins / Hosts |

---

## ⚙️ Configuration Preview (`config.yml`)

```yaml
# Language selection: en (English), hu (Hungarian)
language: en
enabled: true

# The unified skin applied to all players (auto-fetched & cached from Mojang API)
skin-player-name: "Velenci"

# Simple Voice Chat real-time voice pitch shifter
voice-changer:
  enabled: true
  pitch-ratio: 0.67    # < 1.0 is deeper voice, > 1.0 is higher pitch
  window-ms: 30

# Lives & Elimination Settings
lives-system:
  enabled: true
  default-lives: 3
  pvp-only: true       # Only deduct lives on PvP player deaths
  broadcast-life-lost: true
  reveal-name-on-death: true

# Event & Scatter Settings
game:
  border-size: 8000.0
  scatter-safe-margin: 200.0
  min-player-distance: 800.0
  teleport-protection-seconds: 10
  countdown-seconds: 3

# Identity Tracker Configuration
nametag-tracker:
  enabled: true
  duration-seconds: 300   # 5-minute tracking session
  particle-type: "RED_DUST"
  target-vignette: true   # Renders warning vignette on target's screen
  target-bossbar: true
```

---

## ❓ Frequently Asked Questions (FAQ)

> [!TIP]
> **Do regular players need any mods or resource packs?**  
> **No!** All visual disguises, skin masks, overhead nameplate removals, chat filtering, tab masking, and tracker compasses operate purely through server-side Paper mechanics. Players can join with standard vanilla Minecraft clients. Only the optional voice pitch shifter utilizes the Simple Voice Chat mod.

> [!TIP]
> **Can staff members monitor the game without being masked?**  
> **Yes!** Players with the `strangers.bypass` permission can run `/strangers bypass on` at any time to see everyone's genuine skin and real username, allowing seamless moderation.

> [!TIP]
> **What happens if someone disconnects while being tracked?**  
> The Identity Tracker automatically detects the disconnect and pauses the countdown timer. When the target reconnects, the hunter is notified and the track resumes with the exact remaining time.

---

## 🔌 Compatibility & Requirements

| Component | Requirement | Role |
|:---|:---|:---|
| **Server Software** | **Paper** or **Purpur** | Supported: **1.20.x — 1.21+** |
| **Java** | **Java 21+** | Standard modern Minecraft runtime |
| **Simple Voice Chat** | *Optional (Recommended)* | Powers real-time voice pitch distortion |
| **PacketEvents** / **ProtocolLib** | *Optional (Recommended)* | Powers packet-level GameProfile spoofing |

---

<div align="center">

<p style="font-weight: bold;">Protected under the <a href="LICENSE">Strangers Public & Attribution License</a></p>
<p>Code modification is permitted for personal/server use • Public redistribution or resale is strictly prohibited • Mandatory credit to <b>mlnplus</b></p>
<p>Crafted with ❤️ by <a href="https://mln.plus">mlnplus</a></p>

</div>
