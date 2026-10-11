package io.github.brainage04.flashbackserverbound;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * The serverbound records, layout version 1, shared with the 1.8.9 ReplayMod recorder: {@code [u8 kind][body]},
 * big-endian, VarInts and strings as in the Minecraft protocol.
 *
 * <ul>
 *   <li>{@link #HEADER}: {@code [u8 layout version][VarInt protocol][String generator]}, the first record.</li>
 *   <li>{@link #PACKET}: {@code [u8 state][VarInt packet id][body]}, a serverbound packet as encoded for the wire
 *       (before compression and encryption).</li>
 *   <li>{@link #INPUT}, at the end of every client tick: {@code [u32 flags][u8 hotbar slot][u8 attack clicks]
 *       [u8 use clicks][f32 forward][f32 strafe][f32 yaw][f32 pitch][f32 turn yaw][f32 turn pitch]}.</li>
 *   <li>{@link #TICK_START}, empty, at the start of every client tick.</li>
 * </ul>
 */
public final class Records {
    public static final int LAYOUT_VERSION = 1;

    public static final byte HEADER = 0;
    public static final byte PACKET = 1;
    public static final byte INPUT = 2;
    public static final byte TICK_START = 3;

    public static final int STATE_PLAY = 0;
    public static final int STATE_CONFIGURATION = 1;
    public static final int STATE_LOGIN = 2;

    /** Keys as held (key bindings). */
    public static final int F_FORWARD = 1;
    public static final int F_BACK = 1 << 1;
    public static final int F_LEFT = 1 << 2;
    public static final int F_RIGHT = 1 << 3;
    public static final int F_JUMP = 1 << 4;
    public static final int F_SNEAK = 1 << 5;
    public static final int F_SPRINT = 1 << 6;
    public static final int F_ATTACK = 1 << 7;
    public static final int F_USE = 1 << 8;
    public static final int F_PICK = 1 << 9;
    /** A screen is open: keys go to it. */
    public static final int F_SCREEN = 1 << 10;
    /** No player in a world: the other fields are meaningless. */
    public static final int F_NO_PLAYER = 1 << 11;
    /** What the tick's movement input applied, and the player's state at the end of the tick. */
    public static final int F_JUMPING = 1 << 12;
    public static final int F_SHIFTING = 1 << 13;
    public static final int F_SPRINTING = 1 << 14;
    public static final int F_USING_ITEM = 1 << 15;
    public static final int F_ON_GROUND = 1 << 16;

    private static final byte[] TICK_START_RECORD = {TICK_START};
    private static final int INPUT_SIZE = 1 + 4 + 3 + 6 * 4;

    private Records() {
    }

    public static byte[] header(int protocol, String generator) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(HEADER);
        out.write(LAYOUT_VERSION);
        writeVarInt(out, protocol);
        byte[] text = generator.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, text.length);
        out.writeBytes(text);
        return out.toByteArray();
    }

    /** {@code packet}: the packet id (VarInt) and body as encoded for the wire. */
    public static byte[] packet(int state, byte[] packet) {
        byte[] record = new byte[2 + packet.length];
        record[0] = PACKET;
        record[1] = (byte) state;
        System.arraycopy(packet, 0, record, 2, packet.length);
        return record;
    }

    public static byte[] input(int flags, int slot, int attackClicks, int useClicks, float forward, float strafe,
                               float yaw, float pitch, float turnYaw, float turnPitch) {
        return ByteBuffer.allocate(INPUT_SIZE)
                .put(INPUT)
                .putInt(flags)
                .put((byte) slot)
                .put((byte) Math.min(attackClicks, 255))
                .put((byte) Math.min(useClicks, 255))
                .putFloat(forward)
                .putFloat(strafe)
                .putFloat(yaw)
                .putFloat(pitch)
                .putFloat(turnYaw)
                .putFloat(turnPitch)
                .array();
    }

    public static byte[] tickStart() {
        return TICK_START_RECORD;
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }
}
