package io.github.brainage04.flashbackserverbound;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.Recorder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.ref.WeakReference;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.player.Input;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes the serverbound records ({@link Records}) into the replay Flashback is recording, as {@link ServerboundAction}s
 * submitted to Flashback's writer.
 *
 * <p>Everything is written from the client thread, between Flashback's own writes: a packet sent from another
 * thread (keep-alive replies are sent from the network thread) is encoded there and written on the client thread's
 * next task run, between ticks. Flashback writes a whole tick (and any new chunk file with its snapshot) inside its
 * end-of-tick call, so a record never lands inside a snapshot or before a chunk file's first action.
 */
public final class ServerboundRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlashbackServerbound.MOD_ID);
    private static final String GENERATOR = "FlashbackServerbound " + FabricLoader.getInstance()
            .getModContainer(FlashbackServerbound.MOD_ID)
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");

    private static WeakReference<Recorder> headerWrittenFor = new WeakReference<>(null);
    private static boolean encodeFailureLogged;
    private static int attackClicks;
    private static int useClicks;
    private static float turnYaw;
    private static float turnPitch;

    private ServerboundRecorder() {
    }

    /** A packet the client is sending, on any thread, with the protocol it is sent in. */
    public static void sent(Packet<?> packet, ProtocolInfo<?> protocol) {
        Recorder recorder = Flashback.RECORDER;
        if (recorder == null || !recorder.readyToWrite()) {
            return;
        }
        int state = state(protocol.id());
        if (state < 0) {
            return;
        }
        byte[] record;
        try {
            record = Records.packet(state, encode(packet, protocol));
        } catch (RuntimeException e) {
            if (!encodeFailureLogged) {
                encodeFailureLogged = true;
                LOGGER.warn("Could not encode serverbound packet {}; such packets are left out of the replay",
                        packet.type(), e);
            }
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) {
            write(recorder, record);
        } else {
            minecraft.execute(() -> {
                if (Flashback.RECORDER == recorder) {
                    write(recorder, record);
                }
            });
        }
    }

    /** Flashback starts a client tick (client thread). */
    public static void tickStarted(Recorder recorder) {
        write(recorder, Records.tickStart());
    }

    /**
     * Flashback ends a client tick (client thread): the tick's input, written when Flashback writes the tick. On
     * {@code close} (the recording finishing between ticks) no tick was played and nothing is written.
     */
    public static void tickEnded(Recorder recorder, boolean close) {
        int attacks = attackClicks;
        int uses = useClicks;
        float yaw = turnYaw;
        float pitch = turnPitch;
        attackClicks = useClicks = 0;
        turnYaw = turnPitch = 0.0f;
        if (close) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Overlay overlay = minecraft.gui.overlay();
        boolean writesTick = minecraft.level != null && (overlay == null || !overlay.isPausing())
                && !minecraft.isPaused() && !recorder.isPaused()
                && !(minecraft.gui.screen() instanceof LevelLoadingScreen);
        if (writesTick) {
            write(recorder, input(minecraft, attacks, uses, yaw, pitch));
        }
    }

    /** The mouse turned the local player (client thread), in degrees as applied. */
    public static void turned(float yaw, float pitch) {
        turnYaw += yaw;
        turnPitch += pitch;
    }

    /** A click of a key binding was handled (client thread). */
    public static void clicked(KeyMapping key) {
        Options options = Minecraft.getInstance().options;
        if (key == options.keyAttack) {
            attackClicks++;
        } else if (key == options.keyUse) {
            useClicks++;
        }
    }

    private static byte[] input(Minecraft minecraft, int attacks, int uses, float turnYaw, float turnPitch) {
        Options options = minecraft.options;
        int flags = (options.keyUp.isDown() ? Records.F_FORWARD : 0)
                | (options.keyDown.isDown() ? Records.F_BACK : 0)
                | (options.keyLeft.isDown() ? Records.F_LEFT : 0)
                | (options.keyRight.isDown() ? Records.F_RIGHT : 0)
                | (options.keyJump.isDown() ? Records.F_JUMP : 0)
                | (options.keyShift.isDown() ? Records.F_SNEAK : 0)
                | (options.keySprint.isDown() ? Records.F_SPRINT : 0)
                | (options.keyAttack.isDown() ? Records.F_ATTACK : 0)
                | (options.keyUse.isDown() ? Records.F_USE : 0)
                | (options.keyPickItem.isDown() ? Records.F_PICK : 0)
                | (minecraft.gui.screen() != null ? Records.F_SCREEN : 0);
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return Records.input(flags | Records.F_NO_PLAYER, 0, attacks, uses, 0, 0, 0, 0, turnYaw, turnPitch);
        }
        Input applied = player.input.keyPresses;
        flags |= (applied.jump() ? Records.F_JUMPING : 0)
                | (applied.shift() ? Records.F_SHIFTING : 0)
                | (player.isSprinting() ? Records.F_SPRINTING : 0)
                | (player.isUsingItem() ? Records.F_USING_ITEM : 0)
                | (player.onGround() ? Records.F_ON_GROUND : 0);
        return Records.input(flags, player.getInventory().getSelectedSlot(), attacks, uses, player.zza, player.xxa,
                player.getYRot(), player.getXRot(), turnYaw, turnPitch);
    }

    private static void write(Recorder recorder, byte[] record) {
        if (!recorder.readyToWrite()) {
            return;
        }
        if (headerWrittenFor.get() != recorder) {
            headerWrittenFor = new WeakReference<>(recorder);
            submit(recorder, Records.header(SharedConstants.getProtocolVersion(), GENERATOR));
        }
        submit(recorder, record);
    }

    private static void submit(Recorder recorder, byte[] record) {
        recorder.submitCustomTask(writer -> {
            writer.startAction(ServerboundAction.INSTANCE);
            writer.friendlyByteBuf().writeBytes(record);
            writer.finishAction(ServerboundAction.INSTANCE);
        });
    }

    @SuppressWarnings("unchecked")
    private static byte[] encode(Packet<?> packet, ProtocolInfo<?> protocol) {
        ByteBuf buf = Unpooled.buffer();
        try {
            ((StreamCodec<ByteBuf, Packet<?>>) (StreamCodec<?, ?>) protocol.codec()).encode(buf, packet);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            buf.release();
        }
    }

    private static int state(ConnectionProtocol protocol) {
        return switch (protocol) {
            case PLAY -> Records.STATE_PLAY;
            case CONFIGURATION -> Records.STATE_CONFIGURATION;
            case LOGIN -> Records.STATE_LOGIN;
            default -> -1;
        };
    }
}
