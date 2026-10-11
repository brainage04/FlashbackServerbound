package io.github.brainage04.flashbackserverbound;

import com.moulberry.flashback.action.ActionRegistry;
import net.fabricmc.api.ClientModInitializer;

public final class FlashbackServerbound implements ClientModInitializer {
    public static final String MOD_ID = "flashback_serverbound";

    @Override
    public void onInitializeClient() {
        ActionRegistry.register(ServerboundAction.INSTANCE);
    }
}
