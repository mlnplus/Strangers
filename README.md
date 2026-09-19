# Strangers

**Strangers** is a premium Spigot/Paper Minecraft plugin designed for anonymous SMPs, hardcore servers, and social deduction game modes. It enforces total anonymity across the entire server, rendering players completely indistinguishable from one another in skin, name, tab list, and even proximity voice chat.

---

## 🌟 Features

### 👤 Absolute Anonymity
* **Identity Spoofing:** Overwrites players' displayName, tab list name, and scoreboard name tags to `Ismeretlen` (Hungarian) or `Stranger` (English).
* **Skin Synchronization:** Forcefully overrides all players' skins to a shared skin configured in `config.yml`. It supports auto-fetching and intelligent configuration caching by player name (e.g., `Velenci` or `withdrawls_`) to bypass Mojang rate limits.
* **Modproof Protection:** Re-creates player profiles on the network layer using `Bukkit.createProfile`. This hides players' real usernames inside the GameProfile packets, blocking client-side cheats, radars, minimaps (e.g., JourneyMap, VoxelMap), and freecams from resolving their real names.

### 🔌 Simple Voice Chat Voice Changer
* **Voice Pitch Modulation:** Hooks directly into the **Simple Voice Chat API**. It intercepts player microphone packets on the server and applies a real-time DSP pitch shift (e.g., lower pitch to `0.75x`), making proximity voices completely unrecognizable.
* **Safe Loading:** Isolated class loaders ensure the plugin runs perfectly fine even if Simple Voice Chat is not installed on the server.

### 🎭 Cinematic Death Reveal Animation
* **No Chat Spam:** Chat death broadcasts, join messages, and leave messages are completely muted.
* **Theatrical Decode Sequence:** When a player dies, a server-wide animated Title begins:
  * **Intro (1.0s):** The dead player's name is shown fully obfuscated (`&k` matrix scrambling) in gold, accompanied by a low-pitched Wither ambient growl.
  * **Decode (0.5s per letter):** The name resolves letter-by-letter, clicking like a mechanical terminal solver (`UI_BUTTON_CLICK`) for all online players.
  * **Reveal:** Once fully decoded, the name flashes in bold dark red, accompanied by a server-wide lightning strike and heavy anvil crash.
* **Instant Name Ban:** Banned from the server immediately after the animation completes.

### 🛡️ Command, Chat & Autocomplete Blocker
* **Tab Completion Filters:** Removes all real online player names from tab completion suggestions for non-admin players across Paper and ProtocolLib.
* **Whisper, Probing & List Disabler:** Disables `/msg`, `/tell`, `/w`, `/whisper`, `/r`, `/reply`, `/me`, `/list`, `/team`, `/trigger`, `/near`, `/who`, `/online`, `/plist`, and `/listplayers` for regular players.
* **Name Probing Interceptor:** Scans every executed command's arguments and blocks target selectors (`@p`, `@a`, `@r`, `@e`). If a regular player tries to run *any* command containing a real online player's name (e.g., `/ping player`, `/pay "player" 10`), the command is instantly blocked to prevent online player probing.
* **Chat Name Masking:** Automatically sanitizes public chat messages for normal players, masking real player name mentions with `Stranger`.
* **Signed Books Protection:** Automatically overrides book author metadata upon signing to `Stranger` to prevent written book identity leaks.
* **Server List Ping Shield:** Hides the online player sample and hover list in the multiplayer menu (`setHidePlayers(true)`).
* **Advancement Anonymizer:** Replaces player names with `Stranger` or completely mutes advancement announcements in chat.
* **Revive Privacy:** Masks the reviver's identity in the server-wide revive broadcast unless an admin is in bypass mode.

## 🛠️ Commands & Permissions

* `/strangers on` - Enable anonymization, applying skins and nicknames. (Permission: `strangers.admin`)
* `/strangers off` - Disable anonymization, restoring players' original skins, names, and name tags. (Permission: `strangers.admin`)
* `/strangers reload` - Reload the configuration file (`config.yml`). (Permission: `strangers.admin`)

---

## ⚙️ Configuration (`config.yml`)

```yaml
# Strangers Plugin Configuration
# Supported languages: hu, en
language: hu
enabled: true

# Skin to apply to everyone.
# It can dynamically fetch a skin from Mojang API using a player name.
skin-player-name: "Velenci"

# Base64 texture value & signature cache (automatically updated by the plugin)
fallback-skin-player-name: "Velenci"
fallback-skin-value: "ewogICJ0aW1lc3RhbXAiIDogMTc4MzgxMzMzMjQ1MiwKICAicHJvZmlsZUlkIiA6ICJmNjg0MzNhYTQ3NWE0OWUwOGExNGE2MmJlNjRiZDc2OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJWZWxlbmNpIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2IxYTIyNDRhODhmNDAwNDQzNWUwODQ2ZDE2ZjI3OGIyNjVkOGVlYmVmODY5MDMwNmQ3OGYyMjIzZDQxY2Q4NmEiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQog   }
  }
}"
fallback-skin-signature: "Q1VR39IxFtgwWks4VQr5YiFBn+Yfk43Vxrcf9WeHaSGrMmfs5kmrxibG4o5PqEUwEYCLGkmmarvYjlPedYkYgmZorm4ZgiM6kSSaddTTxmQFtqEfq7MR/30xv9GbcRItTH0AUXVdnPb5dQBTD5BMP4LmtXsUR1/rBP99jqkHRx7UB7uijtdTPcTfaVhDydGDDjjuQrEeFuuwVMyNWt7nWq1KF5CvLmhAAxV9B43DMySxGPEflGlVn4ZXVzYuZ0/g7c7RUx3g/kYU995ZtrycYg9mWXmvf/GfLaiH6FWMfwu9mKI0Fxnvrc9qylgPDUzR2QBq7OxVbXI+hrGy3gx6smpKKQrkbam4SiiX9zZj3p44pHik+rnUUdiWQXvzUga/KAcZJ1NXUXzCdwDOYCY/s/lXzq5O9OfcOY51gI84FvHeQ8rUHErqDokZo/fbDpcL2Fj0ljSWz2lrLPEDrkeSol8dhvCUypnL8Rk10MK4n1saXYLTQLoMdnQAhW45SIii0iwYeUdY57hM2b11GthDlfEWe3fUddUbVfwQqLNf521o+R3rvICf8PHLbe+nB9sRbN/0HldBsUa6ZI7e7zsqAMctBNGRAvBRJ6TwAkyBMaxLi+SJg/NSEgLvhRaz+JwLFvBIANpQSmfh25wjN+FO0TCnk5PM9/a2ZWmgvUZuXdU="

# Voice Changer Settings (Simple Voice Chat Hook)
voice-changer:
  enabled: true
  # The pitch ratio. 1.0 is normal, < 1.0 is lower pitch (deeper), > 1.0 is higher pitch.
  pitch-ratio: 0.75
  # The window size in milliseconds for the delay-line crossfader
  window-ms: 30

# Messages Configuration
messages:
  hu:
    chat-format: "&7<Ismeretlen> &f{MESSAGE}"
    join-message: "&cIsmeretlen csatlakozott a játékhoz"
    quit-message: "&cIsmeretlen kilépett a játékból"
    ban-reason: "&cMeghaltál és a kiléted lelepleződött."
    death-subtitle: "&cmeghalt."
    no-permission: "&cEhhez nincs jogosultságod!"
    usage: "&4Használat: &f/strangers [on|off|reload]"
    already-enabled: "&cMár be van kapcsolva!"
    plugin-enabled: "&aStrangers bekapcsolva!"
    already-disabled: "&cMár ki van kapcsolva!"
    plugin-disabled: "&cStrangers kikapcsolva!"
    plugin-reloaded: "&aKonfiguráció újratöltve!"
  en:
    chat-format: "&7<Stranger> &f{MESSAGE}"
    join-message: "&4Stranger joined the game"
    quit-message: "&4Stranger left the game"
    ban-reason: "&cYou and your identity has been revealed."
    death-subtitle: "&cdied."
    no-permission: "&cYou do not have permission to execute this command!"
    usage: "&4Usage: &f/strangers [on|off|reload]"
    already-enabled: "&cThe plugin is already enabled!"
    plugin-enabled: "&aStrangers has been enabled!"
    already-disabled: "&cThe plugin is already disabled!"
    plugin-disabled: "&cStrangers has been disabled!"
    plugin-reloaded: "&aConfiguration has been reloaded!"
