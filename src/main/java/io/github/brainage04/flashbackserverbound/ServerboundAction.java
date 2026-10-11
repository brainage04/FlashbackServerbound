package io.github.brainage04.flashbackserverbound;

import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

/**
 * One serverbound record ({@link Records}) in a Flashback replay. The name ends in {@code optional}, so Flashback
 * without this mod skips the action; with it, playback skips it too: the records are for analysis, not for the
 * replayed world.
 */
public final class ServerboundAction implements Action {
    public static final Identifier NAME = Identifier.fromNamespaceAndPath(FlashbackServerbound.MOD_ID,
            "action/serverbound_optional");
    public static final ServerboundAction INSTANCE = new ServerboundAction();

    private ServerboundAction() {
    }

    @Override
    public Identifier name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        buf.skipBytes(buf.readableBytes());
    }
}
