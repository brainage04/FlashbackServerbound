# FlashbackServerbound

A client-side Fabric mod for Minecraft 26.2 that adds outgoing packets and per-tick input to [Flashback](https://modrinth.com/mod/flashback) recordings.

## Features

It is a separate mod installed beside an unmodified Flashback (0.43.6 or a later 0.43 release); it contains no
Flashback code. The records go into the same replay `.zip` Flashback saves, as an extra action that Flashback skips
when playing: a replay recorded with this mod plays the same in Flashback with or without it installed.

Packets are captured only while Flashback's recorder is ready; packets that fail to encode are omitted with a warning in the client log.

## Getting started

Versions: Minecraft 26.2, Fabric Loader 0.19.3 or newer, Java 25, Flashback 0.43.6 (0.43.x), Fabric API.

1. Install Fabric for Minecraft 26.2 (for example a Fabric instance in Prism Launcher).
2. Download Flashback (0.43.6 or a later 0.43 release) and Fabric API from Modrinth yourself; Flashback is not
   included here and may not be redistributed.
3. Download `flashbackserverbound-1.0.0.jar` from the [v1.0.0 release](https://github.com/brainage04/FlashbackServerbound/releases/tag/v1.0.0)
   and put it in the same `mods` folder as Flashback and Fabric API.

## Commands

No commands or keybinds are added; use Flashback's recording controls.

## Configuration

Nothing to configure: whenever Flashback records, the records are added.

## Compatibility

Replays are saved by Flashback as `.zip` files in `.minecraft/flashback/replays/` of the instance. Recordings work
without this mod (Flashback records and plays them as usual); recordings made without it just lack the player's own
inputs (the packets the client sent and the keys held each tick).

## Documentation

- [docs/FORMAT.md](docs/FORMAT.md) - binary record layout.
- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) - build and production client test commands.

### Licence

MIT. Flashback is not part of this project and is not redistributed: it is "All rights reserved, do not
redistribute"; players download it from Modrinth themselves.
