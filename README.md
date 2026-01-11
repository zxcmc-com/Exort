# Exort Storage Network

> AE2‑inspired storage network for Paper/Purpur servers — terminals, crafting, automation and wireless access, no client mods required.

## What is it?
Exort turns your storage into a searchable network. Items live in the storage database and are accessed through terminals, monitors, and automation buses — all implemented as a Paper/Purpur plugin.

## Features
- **Storage Network**: central Storage block + cables + terminals.
- **Search & Sorting**: quick item search and category sorting in the terminal GUI.
- **Crafting Terminal**: craft directly from network items.
- **Monitors**: show storage fill % or live item counts.
- **Automation**: import/export buses with filters and modes.
- **Wireless Terminal**: access the network within range using a charged item.

## Compatibility
- **Server software**: Paper / Purpur
- **Minecraft**: 1.21.7–1.21.11+
- **Java**: 21

## Installation
1) Build: `./gradlew build`
2) Put the jar into `plugins/` and start the server.
3) On first run Exort creates:
   - `plugins/Exort/config.yml`
   - `plugins/Exort/storage-tiers.yml`
   - `plugins/Exort/recipes.yml`
   - `plugins/Exort/pack/exort.zip`

> Resource pack `exort.zip` is **only required** for `RESOURCE` mode. `VANILLA` works without it.

## Quick Start (in‑game)
1) Place **Storage**.
2) Connect **Wire**.
3) Place a **Terminal** or **Crafting Terminal**.
4) (Optional) Add **Monitor** and **Import/Export Buses**.

## License
This project uses a source‑available license. See `LICENSE.md` for details.
