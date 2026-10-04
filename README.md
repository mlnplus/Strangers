<div align="center">
  <h1>🎭 Strangers</h1>
  <p><b>Anonymous player profiles, proximity voice pitch shifter, and 3-lives hardcore SMP mechanics for Paper servers.</b></p>

  [![Minecraft](https://img.shields.io/badge/Minecraft-1.20%20--%201.21+-brightgreen?style=for-the-badge&logo=minecraft)](https://papermc.io)
  [![Server Software](https://img.shields.io/badge/Server-Paper%20%7C%20Purpur-blue?style=for-the-badge&logo=papermc)](https://papermc.io)
  [![Voice Chat](https://img.shields.io/badge/Voice%20Chat-Simple%20Voice%20Chat-orange?style=for-the-badge)](https://modrinth.com/plugin/simple-voice-chat)
  [![License](https://img.shields.io/badge/License-Custom-red?style=for-the-badge)](LICENSE)

  <br />

  <p>Custom-made for <b>Zxynn</b> and featured in his Anonymous SMP video:<br/>
  <a href="https://www.youtube.com/watch?v=6r69C-k_jww"><b>▶ Watch: "How I Became Minecraft's Greatest Anonymous Killer"</b></a></p>

  <a href="https://www.youtube.com/watch?v=6r69C-k_jww">
    <img src="https://img.youtube.com/vi/6r69C-k_jww/maxresdefault.jpg" width="600" alt="Zxynn Anonymous SMP Video"/>
  </a>
</div>

---

> [!NOTE]
> **Strangers** turns your server into an anonymous survival game where all players share the same skin, names are hidden, and proximity voice is distorted. No client-side mods are required for regular players (Simple Voice Chat is only needed if you want proximity voice).

---

## 🌟 Key Features

- 🎭 **Total Disguise**: Everyone receives the same skin (configurable in `config.yml`) and is renamed to "Stranger". Overhead name tags are hidden, tab list names are masked, and player lists are hidden from the server ping menu.
- 🎙️ **Real-Time Voice Changer**: Native hook for **Simple Voice Chat** that shifts player voice pitch down (default `0.67x`) on proximity voice so no one can recognize who is speaking.
- ❤️ **3-Lives System**: Players start with a set amount of lives (default 3), displayed on their action bar with custom colored hearts (`💛 💛 💛` / `❤️ ❤️` / `🖤` / `☠`). Configurable to only lose lives on PvP deaths.
- ⚡ **Cipher Death Reveal**: When an eliminated player dies, a scrambled title animation decodes their real name letter by letter for all online players before executing the configured penalty (`BAN`, `KICK`, or spectator).
- 🧭 **Identity Tracker NameTag**: Craftable compass tracker. Right-click to choose an online player from a head GUI, then sneak + right-click to start a 5-minute track with a bossbar HUD. Reveals the target's true identity and skin only to the hunter.
- 📖 **Revive Book**: A high-tier craftable book that opens a GUI of dead players. Revives a chosen player with 1 life (each player can only be revived once per game).
- 🌍 **Event Setup & Scatter**: Run `/strangers start` to open a confirmation GUI that sets the world border and safely scatters players onto solid surface terrain under open skies with minimum distance spacing.
- 🛡️ **Information Leak Protection**: Blocks `/msg`, `/tell`, target selectors (`@p`, `@a`), book signatures, and tab completions that could give away who is online.
- 👁️ **Admin Bypass**: Staff with `strangers.bypass` can toggle `/strangers bypass` to see all real names and original skins at any time.

---

## 📜 Crafting Recipes

### 🧭 Identity Tracker
*Crafted in a crafting table to track down another player.*

| Recipe Grid | Ingredients |
| :--- | :--- |
| `[ Eye of Ender ]` `[ Compass ]` `[ Eye of Ender ]`<br/>`[ Compass ]` `[ Name Tag ]` `[ Compass ]`<br/>`[ Eye of Ender ]` `[ Compass ]` `[ Eye of Ender ]` | • **1x** Name Tag<br/>• **4x** Eye of Ender<br/>• **4x** Compass |

---

### 📖 Revive Book
*Right-click to open the Resurrection GUI and revive an eliminated player.*

| Recipe Grid | Ingredients |
| :--- | :--- |
| `[ Netherite ]` `[ Netherite ]` `[ Netherite ]`<br/>`[ God Apple ]` `[ Nether Star ]` `[ God Apple ]`<br/>`[ Netherite ]` `[ Book ]` `[ Netherite ]` | • **5x** Netherite Ingot<br/>• **2x** Enchanted Golden Apple<br/>• **1x** Nether Star<br/>• **1x** Book |

*(All recipes and ingredients can be customized or disabled in `config.yml`)*

---

## ⌨️ Commands & Permissions

| Command | Usage | Permission | Description |
| :--- | :--- | :--- | :--- |
| `/strangers status` | `/strangers status` | `strangers.admin` | View plugin status, active hooks, and quick controls |
| `/strangers start` | `/strangers start [confirm]` | `strangers.admin` | Set border, scatter players, and start the game |
| `/strangers reset` | `/strangers reset [confirm]` | `strangers.admin` | Teleport players to spawn, reset lives, and reset border |
| `/strangers on` | `/strangers on` | `strangers.admin` | Enable disguise mode |
| `/strangers off` | `/strangers off` | `strangers.admin` | Disable disguise mode and restore original player skins |
| `/strangers reload` | `/strangers reload` | `strangers.admin` | Reload config and messages |
| `/strangers bypass` | `/strangers bypass [on\|off]` | `strangers.bypass` | Toggle admin bypass to see real player names and skins |
| `/strangers lives` | `/strangers lives <get\|set\|add\|remove> <player> [amt]` | `strangers.admin` | Check or change player life counts |
| `/strangers revive` | `/strangers revive <player>` | `strangers.admin` | Manually revive an eliminated player |
| `/strangers givetracker` | `/strangers givetracker [player] [amount]` | `strangers.admin` | Give an Identity Tracker item |
| `/strangers giverevivebook` | `/strangers giverevivebook [player] [amount]` | `strangers.admin` | Give a Revive Book item |
| `/strangers deathmessage` | `/strangers deathmessage [on\|off]` | `strangers.admin` | Toggle life lost chat announcements |
| `/strangers deathsound` | `/strangers deathsound [on\|off]` | `strangers.admin` | Toggle global death heartbeat sounds |

---

## ⚙️ Configuration (`config.yml`)

```yaml
# Language: en / hu
language: en
enabled: true

# The skin applied to all players (fetched automatically from Mojang)
skin-player-name: "Velenci"

# Voice pitch changer (requires Simple Voice Chat plugin)
voice-changer:
  enabled: true
  pitch-ratio: 0.67    # < 1.0 is deeper, > 1.0 is higher
  window-ms: 30

# Lives settings
lives-system:
  enabled: true
  default-lives: 3
  pvp-only: true       # Only take lives on PvP deaths
  broadcast-life-lost: true
  reveal-name-on-death: true

# Game and scatter settings
game:
  border-size: 8000.0
  scatter-safe-margin: 200.0
  min-player-distance: 800.0
  teleport-protection-seconds: 10
  countdown-seconds: 3

# Identity Tracker settings
nametag-tracker:
  enabled: true
  duration-seconds: 300
  particle-type: "RED_DUST"
  target-vignette: true
  target-bossbar: true
```

---

## 🔌 Requirements

| Dependency | Required | Notes |
| :--- | :--- | :--- |
| **Paper / Purpur** | **Yes** | Supports **1.20.x — 1.21+** |
| **Java 21+** | **Yes** | Standard modern runtime |
| **PacketEvents** | **Yes** | Required for packet-level identity and skin disguises |
| **Simple Voice Chat** | Optional | Only needed if you want the proximity voice pitch changer |

---

<div align="center">
  <p style="font-weight: bold;">Protected under the <a href="LICENSE">Strangers Public & Attribution License</a></p>
  <p>Code modification is permitted for personal/server use • Public redistribution or resale is strictly prohibited • Mandatory credit to <b>mlnplus</b></p>
  <p>Created with ❤️ by <a href="https://mln.plus">mlnplus</a></p>
</div>
