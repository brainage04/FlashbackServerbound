# Recorded format

Each record is one Flashback action named `flashback_serverbound:action/serverbound_optional`, written into the tick it happened in. The payload is `[u8 kind][body]` (big-endian; VarInts and strings as in the Minecraft protocol):

- `0 header` - `[u8 layout version = 1][VarInt protocol][String generator]`, before the first other record of a recording.
- `1 packet` - `[u8 state: 0 play, 1 configuration, 2 login][VarInt packet id][body]`, the packet as encoded for the wire, before compression and encryption.
- `2 input` - `[u32 flags][u8 hotbar slot][u8 attack clicks][u8 use clicks][f32 forward][f32 strafe][f32 yaw][f32 pitch][f32 turn yaw][f32 turn pitch]`, at the end of every recorded client tick.
- `3 tick start` - empty, at the start of every client tick.

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
`serverbound.tmcpr`.
