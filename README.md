# FlashbackServerbound

A client-side Fabric mod for Minecraft 26.2 that adds the client's own side to [Flashback](https://modrinth.com/mod/flashback)
recordings: every packet the client sends to the server and, for every client tick, the input it played with. Flashback
itself records only what the client receives.

It is a separate mod installed beside an unmodified Flashback (0.43.6 or a later 0.43 release); it contains no
Flashback code. The records go into the same replay `.zip` Flashback saves, as an extra action that Flashback skips
when playing: a replay recorded with this mod plays the same in Flashback with or without it installed.

Versions: Minecraft 26.2, Fabric Loader 0.19.3 or newer, Java 25, Flashback 0.43.6 (0.43.x), Fabric API.

## Install

1. Install Fabric for Minecraft 26.2 (for example a Fabric instance in Prism Launcher).
2. Download Flashback (0.43.6 or a later 0.43 release) and Fabric API from Modrinth yourself; Flashback is not
   included here and may not be redistributed.
3. Download `flashbackserverbound-1.0.0.jar` from the [v1.0.0 release](https://github.com/brainage04/FlashbackServerbound/releases/tag/v1.0.0)
   and put it in the same `mods` folder as Flashback and Fabric API.
4. Nothing to configure: whenever Flashback records, the records are added.

Replays are saved by Flashback as `.zip` files in `.minecraft/flashback/replays/` of the instance. Recordings work
without this mod (Flashback records and plays them as usual); recordings made without it just lack the player's own
inputs (the packets the client sent and the keys held each tick).

## What is recorded

Each record is one Flashback action named `flashback_serverbound:action/serverbound_optional`, written into the tick
it happened in. The payload is `[u8 kind][body]` (big-endian; VarInts and strings as in the Minecraft protocol):

| Kind | Body | Written |
| --- | --- | --- |
| 0 header | `[u8 layout version = 1][VarInt protocol][String generator]` | before the first other record of a recording |
| 1 packet | `[u8 state: 0 play, 1 configuration, 2 login][VarInt packet id][body]`, the packet as encoded for the wire, before compression and encryption | for every packet sent while Flashback records |
| 2 input | `[u32 flags][u8 hotbar slot][u8 attack clicks][u8 use clicks][f32 forward][f32 strafe][f32 yaw][f32 pitch][f32 turn yaw][f32 turn pitch]` | at the end of every recorded client tick |
| 3 tick start | empty | at the start of every client tick |

Input fields:

- flags, bit 0-16: forward, back, left, right, jump, sneak, sprint keys held; attack, use, pick-block buttons held;
  a screen is open; no player in a world; the tick's movement jumped; it sneaked; sprinting, using an item and on the
  ground at the end of the tick. Keys are the key bindings' held state.
- attack and use clicks: the clicks the game handled during the tick.
- forward and strafe: the movement input the tick's physics used, after the sneak and item-use slowdowns.
- yaw and pitch: the rotation at the end of the tick; turn yaw and turn pitch: the rotation the mouse applied since
  the previous input record, in degrees (pitch as clamped by the game; rotation set by the server is not included).

Packets sent between two ticks (after an input record, before the next tick start) are replies to server packets,
such as teleport confirmations and keep-alives; a packet sent from the network thread is written between ticks.

The same records, with the same layout, are written by the serverbound recorder for ReplayMod 1.8.9 into a zip entry
`serverbound.tmcpr`. SparringBots' `tools/pvpdata` reads both.

## Build and verify

```sh
./gradlew --no-daemon build           # the mod JAR (build/libs) and the record layout unit tests
scripts/verify.sh                     # build, then two real clients on a private Xvfb display
```

`scripts/verify.sh` runs the release JAR in Loom's production client with Flashback 0.43.6 and Fabric API, driven by
a client GameTest (`src/verify`, never part of the release JAR):

- `verifyRecording` (Flashback and this mod): creates a singleplayer world, records a short session (walking,
  sprinting, jumping, strafing, sneaking, turning, clicking, throwing a snowball, changing the hotbar slot), reads the
  saved replay's records back on its own and checks them, then plays the replay to its last tick.
- `verifyStockPlayback` (Flashback alone): plays the same replay to its last tick.

Results go to `build/verify/`: `sample.zip` (the replay), `record-summary.txt`, `playback-with-companion.txt`,
`playback-stock.txt` and the screenshots in each run directory.

## Licence

MIT. Flashback is not part of this project and is not redistributed: it is "All rights reserved, do not
redistribute"; players download it from Modrinth themselves.
