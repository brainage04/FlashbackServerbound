package io.github.brainage04.flashbackserverbound;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * The records byte for byte as the shared layout defines them; the expected bytes are what the dataset tool's own
 * encoder ({@code SparringBots tools/pvpdata/tests/synth_serverbound.py}) writes for the same values.
 */
class RecordsTest {
    private static byte[] hex(String text) {
        return HexFormat.of().parseHex(text);
    }

    @Test
    void header() {
        assertArrayEquals(hex("000188061a466c6173686261636b536572766572626f756e6420312e302e30"),
                Records.header(776, "FlashbackServerbound 1.0.0"));
        assertArrayEquals(hex("0001ac020178"), Records.header(300, "x"));
    }

    @Test
    void packet() {
        assertArrayEquals(hex("01011d010203"), Records.packet(Records.STATE_CONFIGURATION, hex("1d010203")));
    }

    @Test
    void input() {
        int flags = Records.F_FORWARD | Records.F_SPRINT | Records.F_SPRINTING | Records.F_ON_GROUND;
        byte[] record = Records.input(flags, 4, 2, 1, 1.0f, -0.5f, 370.25f, -12.5f, 3.75f, -0.5f);
        assertArrayEquals(hex("02000140410402013f800000bf00000043b92000c148000040700000bf000000"), record);
        assertEquals(32, record.length);
    }

    @Test
    void clicksSaturateAtOneByte() {
        byte[] record = Records.input(0, 0, 300, 256, 0, 0, 0, 0, 0, 0);
        assertEquals((byte) 255, record[6]);
        assertEquals((byte) 255, record[7]);
    }

    @Test
    void tickStart() {
        assertArrayEquals(new byte[] {Records.TICK_START}, Records.tickStart());
    }

    @Test
    void flagBitsMatchTheLayout() {
        int[] flags = {Records.F_FORWARD, Records.F_BACK, Records.F_LEFT, Records.F_RIGHT, Records.F_JUMP,
                Records.F_SNEAK, Records.F_SPRINT, Records.F_ATTACK, Records.F_USE, Records.F_PICK, Records.F_SCREEN,
                Records.F_NO_PLAYER, Records.F_JUMPING, Records.F_SHIFTING, Records.F_SPRINTING,
                Records.F_USING_ITEM, Records.F_ON_GROUND};
        for (int bit = 0; bit < flags.length; bit++) {
            assertEquals(1 << bit, flags[bit]);
        }
    }
}
