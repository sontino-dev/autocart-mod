# AutoCart Mod

Standalone Fabric mod for **Minecraft 1.21.4** extracted from [VCore-0.8](https://github.com/ftl2ndofficial/VCore-0.8).

Automates TNT Minecart combat setups — place rails, drop cart, ignite, shoot with crossbow.

## Features
- **Bow Mode** — auto-deploys cart on bow release at trajectory landing point
- **CrossBow Mode** — manual trigger, places rail+cart+flint+shoot sequence
- **Cart Aura** — auto-triggers on totem pop events (CrossBow mode)
- **ReFill** — auto-refill TNT Minecart slot from inventory (Normal / Legit)
- **Silent Rotation** — rotates head server-side, movement stays natural
- **ModMenu Integration** — full config screen via ModMenu

## Requirements
- Minecraft **1.21.4**
- Fabric Loader ≥ 0.16.0
- Fabric API
- [ModMenu](https://modrinth.com/mod/modmenu) (recommended, optional)

## Build
```bash
./gradlew build
```
Output JAR: `build/libs/autocart-1.0.0.jar`

## Config
Saved to `.minecraft/config/autocart.json`. Editable via ModMenu.

## GitHub Actions
Push to `main` → auto-build → JAR uploaded as artifact.

## Credits
Original AutoCart module by `ftl2ndofficial` / VCore team.
