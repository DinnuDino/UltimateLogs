# ⚡ UltimateLogs (Paper 26.2 & 1.21.x)

[![Modrinth](https://img.shields.io/badge/Modrinth-Download-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/project/ultimatelogs)
[![GitHub](https://img.shields.io/badge/GitHub-Repository-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/DinnuDino/UltimateLogs)

**Enterprise Logging, Live Inventory & EnderChest Inspection, Coordinate Breadcrumbs, Snapshot Archive, and Death Investigation Core.**

Built for **Paper 26.x (Paper 26.1, 26.2, 26.3)** and **Minecraft 1.21.x Game Drops (1.21.1 – 1.21.11)** on **Java 21–25**.

---

## 🌟 Key Features

### 1. Live Real-Time Inventory & Ender Chest Inspection
- **10-Tick Real-Time Sync:** Inspect active players in real time without interfering with their gameplay.
- **Placing & Taking Enabled:** Authorized staff can take, swap, place, and shift-click items directly into the player's live inventory or Ender Chest.
- **Armor Equipping:** Direct armor placement into slots 2–5 with matching armor types and sound feedback.
- **Instant Client Reflection:** Any item placed or taken reflects on the online target player in real time.

### 2. Point-in-Time Historical Snapshots & Rollbacks
- Automated snapshots on **player join, quit, container close, death, and periodic intervals**.
- Inspect what a player held at any minute, hour, or date in the past (`/inv <player> 10m` or `/inv <player> 29/10/2026`).
- **Snapshot Protection:** Placing items into historical snapshots is strictly blocked to maintain snapshot integrity, while taking items is enabled for safe loot recovery.

### 3. Offline Player Container Editing & Auto-Rejoin Sync
- Inspect inventories and Ender Chests even when the player is **offline**.
- Confiscate illegal items or deposit reimbursement items into the offline container.
- When the player rejoins the server, UltimateLogs automatically applies all pending offline modifications.

### 4. Coordinate Breadcrumbs & 1-Click Teleportation (`/pos`)
- Real-time coordinate tracking showing current world, XYZ coordinates, yaw, pitch, and health.
- **1-Click Teleport:** Click the interactive `[CLICK TO TELEPORT]` chat button or teleport to any breadcrumb spot from the history GUI.

### 5. Death Investigation & Item Recovery (`/deaths`)
- Chronological archive of all player deaths, causes, killer info, XYZ coordinates, and drop counts.
- Inspect the exact inventory and Ender Chest at the moment of death, with safe item recovery options.

### 6. Ironclad Anti-Dupe & GUI Security
- Centralized GUI controller with PersistentDataContainer (PDC) button tagging.
- Menu buttons, back arrows, close barriers, and divider panes can **never** be taken, shift-clicked, or hotbar-swapped into personal inventories.
- Automatic cleaning clears client-predicted ghost items on click, drag, close, or disconnect.

---

## 🛠 Commands & Aliases

| Command | Aliases | Permission | Description |
|---|---|---|---|
| `/ultimatelogs` | `/ulogs`, `/logs`, `/auditlogs` | `ultimatelogs.view` | Opens interactive Stage 1 menu or queries audit logs. |
| `/inv <player> [time\|history\|live]` | `/invsee`, `/uinv` | `ultimatelogs.invsee` | Live or historical inventory inspection with live sync. |
| `/ec [player] [time\|history\|live]` | `/enderchest`, `/uec` | `ultimatelogs.enderchest` | Live or historical Ender Chest inspection. |
| `/pos <player> [time\|history]` | `/position`, `/where` | `ultimatelogs.position` | Coordinate breadcrumbs, live tracking, and teleportation. |
| `/deaths <player> [page]` | `/deathlogs`, `/udeaths` | `ultimatelogs.deaths` | View all historical deaths, inspect death items, recover loot. |
| `/logs flush` | — | `ultimatelogs.admin` | Force flush async database queue to disk. |
| `/logs reload` | — | `ultimatelogs.admin` | Reloads `config.yml` settings. |

---

## 🔑 Permissions

* `ultimatelogs.*` — Full access to all UltimateLogs features and GUIs *(Default: OP)*.
* `ultimatelogs.admin` — Access to `/logs flush` and `/logs reload`.
* `ultimatelogs.view` — Access to `/logs` GUI hub.
* `ultimatelogs.invsee` — Access to `/inv` live and snapshot inspection.
* `ultimatelogs.enderchest` — Access to `/ec <player>` inspection.
* `ultimatelogs.position` — Access to `/pos` tracking and teleportation.
* `ultimatelogs.deaths` — Access to `/deaths` investigation archive.
* `ultimatelogs.restore` — Permission to extract items from historical snapshots.

---

## 💾 Database Architecture

Supports **SQLite** (default local file `ultimate_logs.db`) and **MySQL / MariaDB** (via HikariCP connection pool):
- `player_sessions`: Joins, quits, durations, platform, and IP address.
- `player_position_snapshots`: Position tracker with start time, end time, and duration seconds.
- `player_inventory_snapshots`: Full inventory, armor, offhand, and Ender Chest data.
- `player_death_records`: Complete death logs including killer, coordinates, XP lost, and snapshot IDs.
- `pending_offline_modifications`: Queued offline item changes synced when players rejoin.

---

## 🔨 Building from Source

```bash
mvn clean package
```

The compiled shaded artifact will be located in `target/UltimateLogs-1.0.0-Paper26.2.jar`.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE) - see the LICENSE file for details.
